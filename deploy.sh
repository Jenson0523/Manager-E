#!/bin/bash
set -e

echo "=== [1/6] Pulling latest code ==="
cd /opt/tengyei-src
git stash --include-untracked 2>/dev/null || true
git pull origin main
git stash drop 2>/dev/null || true

echo "=== [2/6] Copying production config ==="
cp /opt/tengyei/config/application-prod.yml /opt/tengyei-src/tengyei-backend/app/src/main/resources/application-prod.yml

echo "=== [3/6] Building backend (with tests) ==="
cd /opt/tengyei-src/tengyei-backend
# 测试失败即中止部署(H2 内存库,无需 MySQL/Redis);曾因 skipTests 把挂测试的代码发上线
mvn package -q
cp app/target/app-1.0.0-SNAPSHOT.jar /opt/tengyei/app-1.0.0-SNAPSHOT.jar

echo "=== [4/6] Building frontend ==="
cd /opt/tengyei-src/tengyei-frontend
npm install --legacy-peer-deps -q
npm run build -q
rm -rf /opt/tengyei/frontend/dist
cp -r dist /opt/tengyei/frontend/

echo "=== [5/6] Restarting backend ==="
pkill -f 'app-1.0.0-SNAPSHOT.jar' || echo 'no process to kill'
sleep 2
cd /opt/tengyei
nohup java -Xmx384m -Xms256m -jar app-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod > app.log 2>&1 &

echo "=== [6/6] Health check ==="
# 探活登录接口(未授权也应返回 HTTP 响应),90 秒内起不来即失败
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/auth/login || true)
  if [ "$code" != "000" ] && [ -n "$code" ]; then
    echo "Backend UP (http $code, ${i}x3s)"
    echo "Frontend: http://tf.alois.bond"
    exit 0
  fi
  sleep 3
done
echo "!!! Backend FAILED to start within 90s — check /opt/tengyei/app.log"
tail -30 /opt/tengyei/app.log
exit 1
