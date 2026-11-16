# Deploy Kurulumu (GitHub Actions → S3 → SSM → EC2)

Akış:
- **PR açılınca** (`ci.yml`): GitHub runner'da `./mvnw verify` çalışır. AWS'ye dokunmaz.
- **main'e merge olunca** (`deploy.yml`): testler tekrar çalışır → jar S3'e yüklenir →
  SSM Run Command ile EC2'de jar indirilir, servis yeniden başlatılır, `/actuator/health` kontrol edilir.

EC2'ye SSH yok, inbound port açmaya gerek yok. GitHub AWS'ye OIDC ile girer, uzun ömürlü erişim anahtarı tutulmaz.

## 1. S3 bucket
Örn. `campushub-artifacts-<hesap-id>`. Public erişim kapalı kalsın. İsteğe bağlı: `campushub-backend/`
prefix'i için 30 günde silen bir lifecycle kuralı.

## 2. EC2 instance
- Amazon Linux 2023 (SSM Agent hazır gelir). Security group'ta inbound 22'ye gerek yok;
  sadece uygulama portu (8080, ya da önüne konacak load balancer/nginx) açılır.
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
        "Resource": "arn:aws:s3:::<BUCKET>/campushub-backend/*"
      }]
    }
    ```
- Instance'a bir kez kurulum (Session Manager ile bağlanıp):
  ```bash
  sudo dnf install -y java-21-amazon-corretto-headless
  sudo useradd --system --home /opt/campushub --shell /sbin/nologin campushub
  sudo mkdir -p /opt/campushub && sudo chown campushub:campushub /opt/campushub
  # deploy/campushub.service dosyasını /etc/systemd/system/campushub.service olarak koy
  sudo systemctl daemon-reload && sudo systemctl enable campushub
  ```
  Ortam değişkenleri (DB şifresi vb.) `/opt/campushub/app.env` içine konabilir.
- Systems Manager → Fleet Manager'da instance "Online" görünmeli.

## 3. GitHub OIDC rolü
1. IAM → Identity providers → Add provider → OpenID Connect
   - URL: `https://token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`
2. IAM → Roles → Web identity → bu provider. Trust policy:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Principal": { "Federated": "arn:aws:iam::<HESAP_ID>:oidc-provider/token.actions.githubusercontent.com" },
       "Action": "sts:AssumeRoleWithWebIdentity",
       "Condition": {
         "StringEquals": {
           "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
           "token.actions.githubusercontent.com:sub": "repo:batubond007/campushub-backend:ref:refs/heads/main"
         }
       }
     }]
   }
   ```
   (Sadece `main` branch'i bu rolü alabilir; PR'lar alamaz.)
3. Rolün izinleri:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": "s3:PutObject",
         "Resource": "arn:aws:s3:::<BUCKET>/campushub-backend/*"
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
Repo → Settings → Secrets and variables → Actions → **Variables**:

| Variable          | Örnek                                          |
|-------------------|------------------------------------------------|
| `AWS_REGION`      | `eu-central-1`                                 |
| `AWS_ROLE_ARN`    | `arn:aws:iam::123456789012:role/github-deploy` |
| `S3_BUCKET`       | `campushub-artifacts-123456789012`             |
| `EC2_INSTANCE_ID` | `i-0abc123...`                                 |

Settings → Branches → `main` için branch protection: "Require status checks" → `test` seçilsin,
böylece testler geçmeden merge edilemez.
