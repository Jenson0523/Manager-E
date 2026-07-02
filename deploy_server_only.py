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
        print(out[-4000:])
    if err:
        print(err[-1000:])
    return out, err, stdout.channel.recv_exit_status()

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    
    print("=== Server deployment started ===")
    
    # Pull latest code (with stash to avoid local changes conflict)
    run_ssh(c, "cd /opt/tengyei-src && git stash --include-untracked 2>/dev/null || true && git pull origin main && git stash drop 2>/dev/null || true", timeout=120)
    
    # Run deploy script
    out, err, exit_status = run_ssh(c, "bash /opt/tengyei-src/deploy.sh", timeout=600)
    
    print(f"\n=== Deploy finished (exit code: {exit_status}) ===")
    
    if exit_status == 0:
        print("✅ Deployment successful!")
        print("\n=== Verifying ===")
        run_ssh(c, "ps aux | grep 'app-1.0.0-SNAPSHOT' | grep -v grep")
        run_ssh(c, "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/auth/login")
        run_ssh(c, "curl -s -o /dev/null -w '%{http_code}' http://localhost/")
        print("\n✅ Verification complete!")
        print("Frontend: http://tf.alois.bond")
        print("Backend API: http://tf.alois.bond/api/")
    else:
        print("❌ Deployment failed!")
    
    c.close()

if __name__ == "__main__":
    main()
