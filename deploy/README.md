# Deploy Kurulumu (GitHub Actions → S3 → SSM → EC2)

Backend ve frontend **aynı EC2**'de çalışır:

```
İnternet ──80──▶ nginx ─┬─ /api/*  ──▶ Spring Boot (127.0.0.1:8080)
                        └─ diğer   ──▶ /var/www/campushub/current (frontend dist/)
```

Akış (iki repo da aynı deseni kullanır):
- **PR açılınca** (`ci.yml`): backend'de `./mvnw verify`, frontend'de `npm run build`. AWS'ye dokunmaz.
- **main'e merge olunca** (`deploy.yml`):
  - backend: testler → jar S3'e → SSM ile EC2'de jar değiştirilir, servis yeniden başlatılır, `/actuator/health` kontrol edilir.
  - frontend: build → `dist/` tar.gz olarak S3'e → SSM ile `/var/www/campushub/releases/<sha>/` içine açılır,
    `current` symlink'i atomik olarak yeni release'e çevrilir (son 5 release tutulur, geri dönmek için symlink'i eskisine çevirmek yeter).

EC2'ye SSH yok. GitHub AWS'ye sadece deploy izinleri olan bir IAM kullanıcısının access key'i ile girer.
(OIDC daha iyi olurdu ama bu hesap AWS'nin "proje" deneyiminde; IAM Identity providers için "gelişmiş
özellikler"i açmak gerekiyor, o da geri alınamıyor ve harcama limitlerini siliyor.)

**Kural:** Backend endpoint'leri `/api/...` altında olmalı; nginx sadece `/api/` prefix'ini backend'e iletir.
Frontend aynı origin'den çağırdığı için CORS gerekmez ve `VITE_API_BASE_URL` boş bırakılır.

## 1. S3 bucket
Örn. `campushub-artifacts-<hesap-id>`. Public erişim kapalı kalsın. İsteğe bağlı: `campushub-backend/`
ve `campushub-frontend/` prefix'leri için 7 günde silen bir lifecycle kuralı (+ yarım kalan multipart upload'lar 1 gün).

## 2. EC2 instance
- Amazon Linux 2023 (SSM Agent hazır gelir).
- **Security group inbound:** sadece 80 (HTTPS eklenince 443). 22 ve 8080 **açılmaz**; backend'e
  sadece nginx üzerinden erişilir.
- Instance'ın dışarıya HTTPS (443) çıkışı olmalı (SSM ve S3'e erişmek için).
- **Instance role** (IAM → Roles → EC2):
  - `AmazonSSMManagedInstanceCore` (AWS yönetimli policy)
  - S3 okuma:
    ```json
    {
      "Version": "2012-10-17",
      "Statement": [{
        "Effect": "Allow",
        "Action": "s3:GetObject",
        "Resource": [
          "arn:aws:s3:::<BUCKET>/campushub-backend/*",
          "arn:aws:s3:::<BUCKET>/campushub-frontend/*"
        ]
      }]
    }
    ```
- Instance'a bir kez kurulum: **`deploy/ec2-user-data.sh` içeriğini launch sırasında "User data"
  alanına yapıştırmak yeterli** (swap, Java, nginx, servis ve nginx config'i otomatik kurar; log:
  `/var/log/cloud-init-output.log`). Elle yapmak istersen (Session Manager ile bağlanıp) özeti:
  ```bash
  # Backend
  sudo dnf install -y java-21-amazon-corretto-headless
  sudo useradd --system --home /opt/campushub --shell /sbin/nologin campushub
  sudo mkdir -p /opt/campushub && sudo chown campushub:campushub /opt/campushub
  # deploy/campushub.service dosyasını /etc/systemd/system/campushub.service olarak koy
  echo 'SERVER_ADDRESS=127.0.0.1' | sudo tee -a /opt/campushub/app.env   # backend sadece localhost'u dinlesin
  sudo systemctl daemon-reload && sudo systemctl enable campushub

  # Frontend + reverse proxy
  sudo dnf install -y nginx
  sudo mkdir -p /var/www/campushub/releases
  # deploy/nginx-campushub.conf dosyasını /etc/nginx/conf.d/campushub.conf olarak koy
  sudo nginx -t && sudo systemctl enable --now nginx
  ```
  Ortam değişkenleri (DB şifresi vb.) `/opt/campushub/app.env` içine konabilir.
  İlk frontend deploy'una kadar `current` symlink'i olmadığı için nginx 404/500 döner; bu normal.
- Systems Manager → Fleet Manager'da instance "Online" görünmeli.

## 3. GitHub deploy kullanıcısı (IAM user + access key)
1. IAM → Policies → Create policy (`campushub-github-deploy`), JSON aşağıda.
2. IAM → Users → Create user `campushub-github-deploy` (konsol erişimi **yok**), "Attach policies directly"
   → `campushub-github-deploy`.
3. Kullanıcı → Security credentials → Create access key → "Application running outside AWS".
   Key'ler sadece bir kez gösterilir; doğrudan GitHub'a (bkz. 4) girilir, başka yere kaydedilmez.
   Ara sıra yeni key oluşturup eskisini silmek iyi olur.

Policy:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": "s3:PutObject",
         "Resource": [
           "arn:aws:s3:::<BUCKET>/campushub-backend/*",
           "arn:aws:s3:::<BUCKET>/campushub-frontend/*"
         ]
       },
       {
         "Effect": "Allow",
         "Action": "ssm:SendCommand",
         "Resource": [
           "arn:aws:ec2:<REGION>:<HESAP_ID>:instance/<INSTANCE_ID>",
           "arn:aws:ssm:<REGION>::document/AWS-RunShellScript"
         ]
       },
       {
         "Effect": "Allow",
         "Action": "ssm:GetCommandInvocation",
         "Resource": "*"
       }
     ]
   }
   ```

## 4. GitHub ayarları
**İki repoda da** (backend ve frontend), değerler aynı:

1. Settings → Environments → **New environment** `production`
   - **Deployment branches and tags** → "Selected branches and tags" → `main` ekle
     (key'leri sadece main'deki deploy kullanabilir).
   - **Environment secrets:** `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
2. Settings → Secrets and variables → Actions → **Variables** (repo seviyesinde):

| Variable          | Değer                              |
|-------------------|------------------------------------|
| `AWS_REGION`      | `eu-north-1`                       |
| `S3_BUCKET`       | `campushub-artifacts-591424882052` |
| `EC2_INSTANCE_ID` | `i-0a4b05824185a5ac0`              |

Settings → Branches → `main` için branch protection: "Require status checks" → backend'de `test`,
frontend'de `build` seçilsin, böylece CI geçmeden merge edilemez.
