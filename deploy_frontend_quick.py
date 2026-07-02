import paramiko

HOST = "107.173.154.187"
USER = "root"
PASSWORD = "78GEalF10J9t8VerAs"

def run_ssh(c, cmd, timeout=300):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    if out:
        print(out[-3000:])
    if err:
        print("[stderr]", err[-2000:])
    return out, err, stdout.channel.recv_exit_status()

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    print("=== 前端快速部署 ===\n")

    # 拉取最新代码
    print("=== [1/2] 拉取最新代码 ===")
    run_ssh(c, "cd /opt/tengyei-src && git fetch origin main")
    run_ssh(c, "cd /opt/tengyei-src && git reset --hard origin/main")
    run_ssh(c, "cd /opt/tengyei-src && git log --oneline -1")

    # 前端构建
    print("\n=== [2/2] 前端构建 ===")
    out, err, code = run_ssh(c,
        "cd /opt/tengyei-src/tengyei-frontend && npm run build 2>&1 | tail -20",
        timeout=300)
    if code != 0:
        print(f"前端构建失败 (exit {code})")
        c.close()
        return

    # 复制到 nginx 目录
    print("\n=== 复制到 nginx 目录 ===")
    run_ssh(c, "cp -r /opt/tengyei-src/tengyei-frontend/dist/. /opt/tengyei/frontend/")

    # 验证
    print("\n=== 验证 ===")
    run_ssh(c, "curl -s -o /dev/null -w 'Frontend HTTP: %{http_code}\\n' http://localhost/")
    print("\n✅ 前端部署完成！")
    c.close()

if __name__ == "__main__":
    main()
