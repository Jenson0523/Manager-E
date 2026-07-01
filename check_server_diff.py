import paramiko

HOST = "107.173.154.187"
USER = "root"
PASSWORD = "78GEalF10J9t8VerAs"

def run_ssh(c, cmd, timeout=120):
    print(f"\n$ {cmd}")
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    print(out)
    if err:
        print(err)
    return out, err

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    run_ssh(c, "cd /opt/tengyei-src && git status")
    run_ssh(c, "cd /opt/tengyei-src && git diff -- tengyei-backend/app/src/main/java/com/tengyei/controller/UserInfoController.java")
    run_ssh(c, "cd /opt/tengyei-src && git diff -- tengyei-backend/app/src/main/java/com/tengyei/service/DashboardService.java")
    c.close()

if __name__ == "__main__":
    main()
