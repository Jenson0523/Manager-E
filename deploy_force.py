import paramiko
import time

HOST = "107.173.154.187"
USER = "root"
PASSWORD = "78GEalF10J9t8VerAs"

def run_ssh(c, cmd, timeout=600):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    if out:
        print(out[-5000:])
    if err:
        print("[stderr]", err[-2000:])
    return out, err, stdout.channel.recv_exit_status()

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    print("=== 连接成功，开始部署 ===\n")

    # Step 1: 强制覆盖服务器本地改动，拉取最新代码
    print("=== [1/5] 强制重置服务器本地改动，拉取最新代码 ===")
    run_ssh(c, "cd /opt/tengyei-src && git fetch origin main")
    run_ssh(c, "cd /opt/tengyei-src && git reset --hard origin/main")
    run_ssh(c, "cd /opt/tengyei-src && git log --oneline -3")

    # Step 2: Maven 构建后端
    print("\n=== [2/5] Maven 构建后端（约3-5分钟）===")
    out, err, code = run_ssh(c,
        "cd /opt/tengyei-src/tengyei-backend && mvn clean package -DskipTests -q 2>&1 | tail -30",
        timeout=480)
    if code != 0:
        print(f"❌ Maven 构建失败 (exit {code})，终止部署")
        c.close()
        return

    # Step 3: 前端构建
    print("\n=== [3/5] 前端构建 ===")
    out, err, code = run_ssh(c,
        "cd /opt/tengyei-src/tengyei-frontend && npm run build 2>&1 | tail -20",
        timeout=300)
    if code != 0:
        print(f"❌ 前端构建失败 (exit {code})，终止部署")
        c.close()
        return

    # Step 4: 停止旧进程，替换 JAR，复制前端
    print("\n=== [4/5] 停止旧进程，部署新版本 ===")
    run_ssh(c, "pkill -f 'app-1.0.0-SNAPSHOT' || true")
    run_ssh(c, "sleep 3")
    run_ssh(c, "cp /opt/tengyei-src/tengyei-backend/app/target/app-1.0.0-SNAPSHOT.jar /opt/tengyei/app-1.0.0-SNAPSHOT.jar")
    run_ssh(c, "cp -r /opt/tengyei-src/tengyei-frontend/dist/. /opt/tengyei/frontend/")

    # Step 5: 启动后端
    print("\n=== [5/5] 启动后端服务 ===")
    run_ssh(c, "cd /opt/tengyei && nohup java -jar app-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod > /opt/tengyei/app.log 2>&1 &")
    print("等待后端启动（15秒）...")
    time.sleep(15)

    # 验证
    print("\n=== 验证部署结果 ===")
    run_ssh(c, "ps aux | grep 'app-1.0.0-SNAPSHOT' | grep -v grep")
    run_ssh(c, "curl -s -o /dev/null -w 'Backend HTTP: %{http_code}\\n' http://localhost:8080/api/v1/auth/login -X POST -H 'Content-Type: application/json' -d '{\"username\":\"test\",\"password\":\"test\"}'")
    run_ssh(c, "curl -s -o /dev/null -w 'Frontend HTTP: %{http_code}\\n' http://localhost/")
    print("\n✅ 部署完成！")
    print("访问地址: http://tf.alois.bond")
    c.close()

if __name__ == "__main__":
    main()
