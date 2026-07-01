#!/bin/bash
set -e

echo "=== [1/6] Pulling latest code ==="
cd /opt/tengyei-src
git pull origin main

echo "=== [2/6] Copying production config ==="
cp /opt/tengyei/config/application-prod.yml /opt/tengyei-src/tengyei-backend/app/src/main/resources/application-prod.yml

echo "=== [3/6] Building backend ==="
cd /opt/tengyei-src/tengyei-backend
mvn package -DskipTests -q
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
sleep 5

echo "=== [6/6] Deploy complete ==="
ps aux | grep 'app-1.0.0-SNAPSHOT' | grep -v grep
echo "Frontend: http://tf.alois.bond"
