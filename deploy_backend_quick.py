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
    print("=== 快速后端修复部署 ===\n")

    # Step 1: 拉取最新代码
    print("=== [1/3] 拉取最新代码 ===")
    run_ssh(c, "cd /opt/tengyei-src && git fetch origin main")
    run_ssh(c, "cd /opt/tengyei-src && git reset --hard origin/main")
    run_ssh(c, "cd /opt/tengyei-src && git log --oneline -1")

    # Step 2: Maven 构建后端
    print("\n=== [2/3] Maven 构建后端（约3-5分钟）===")
    out, err, code = run_ssh(c,
        "cd /opt/tengyei-src/tengyei-backend && mvn clean package -DskipTests -q 2>&1 | tail -20",
        timeout=480)
    if code != 0:
        print(f"Maven 构建失败 (exit {code})，终止部署")
        c.close()
        return

    # Step 3: 停止旧进程，替换 JAR，启动
    print("\n=== [3/3] 停止旧进程，替换 JAR，启动 ===")
    run_ssh(c, "pkill -f 'app-1.0.0-SNAPSHOT' || true")
    run_ssh(c, "sleep 3")
    run_ssh(c, "cp /opt/tengyei-src/tengyei-backend/app/target/app-1.0.0-SNAPSHOT.jar /opt/tengyei/app-1.0.0-SNAPSHOT.jar")
    run_ssh(c, "cd /opt/tengyei && nohup java -jar app-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod > /opt/tengyei/app.log 2>&1 &")
    print("等待后端启动（15秒）...")
    time.sleep(15)

    # 验证
    print("\n=== 验证部署结果 ===")
    run_ssh(c, "ps aux | grep 'app-1.0.0-SNAPSHOT' | grep -v grep | head -2")
    run_ssh(c, "curl -s -o /dev/null -w 'Backend HTTP: %{http_code}\\n' -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' --data-raw '{\"username\":\"test\",\"password\":\"test\"}'")
    print("\n✅ 后端部署完成！")
    c.close()

if __name__ == "__main__":
    main()
