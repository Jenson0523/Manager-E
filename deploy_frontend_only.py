import paramiko
import time

HOST = "107.173.154.187"
USER = "root"
PASSWORD = "78GEalF10J9t8VerAs"

def run_ssh(c, cmd, timeout=300):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    if out:
        print(out[-3000:])
    if err:
        print("[stderr]", err[-1500:])
    return out, err, stdout.channel.recv_exit_status()

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    print("=== 快速重新部署前端 ===\n")

    # Pull latest
    run_ssh(c, "cd /opt/tengyei-src && git pull origin main", timeout=60)
    
    # 前端重新构建
    print("\n=== 前端重新构建 ===")
    out, err, code = run_ssh(c,
        "cd /opt/tengyei-src/tengyei-frontend && npm run build 2>&1 | tail -30",
        timeout=180)
    if code != 0:
        print(f"❌ 前端构建失败 (exit {code})")
        c.close()
        return

    # 复制前端到部署目录
    run_ssh(c, "cp -r /opt/tengyei-src/tengyei-frontend/dist/. /opt/tengyei/frontend/")
    
    # 验证
    run_ssh(c, "curl -s -o /dev/null -w 'Frontend HTTP: %{http_code}\\n' http://localhost/")
    print("\n✅ 前端重新部署完成！")
    print("访问地址: http://tf.alois.bond")
    c.close()

if __name__ == "__main__":
    main()
