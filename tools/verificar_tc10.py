"""Recorrido navegador real TC-10: base temporal/JAR/Vite propios; nunca usa la BD instalada."""
from pathlib import Path
import json,os,re,secrets,shutil,signal,socket,subprocess,tempfile,time,urllib.request

ROOT=Path(__file__).resolve().parents[1]
JAVA=Path(os.environ.get('JAVA_HOME',r'C:\Program Files\Java\jdk-24'))/'bin'/('java.exe' if os.name=='nt' else 'java')
PG=Path(os.environ.get('PG_BIN',r'C:\Program Files\PostgreSQL\18\bin'))
def exe(name): return PG/(name+'.exe' if os.name=='nt' else name)
BASE=Path(tempfile.mkdtemp(prefix='tic-tc10-real-'))
CLUSTER=BASE/'cluster'
env=dict(os.environ)
env['PATH']=str(JAVA.parent)+os.pathsep+env['PATH']
ADMIN=secrets.token_urlsafe(32); APP=secrets.token_urlsafe(32)
ROOT_PASSWORD='TIC10-'+secrets.token_urlsafe(24)
TEST_PASSWORD='Empleado-'+secrets.token_urlsafe(24)
EMAIL='tc10-root-'+secrets.token_hex(5)+'@example.test'
private=[ADMIN,APP,ROOT_PASSWORD,TEST_PASSWORD]
def redact(text):
    for value in private: text=text.replace(value,'[REDACTED]')
    return re.sub(r'(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])','[REDACTED_TOKEN]',text)
def port():
    with socket.socket() as sock: sock.bind(('127.0.0.1',0)); return sock.getsockname()[1]
ports=set()
while len(ports)<3: ports.add(port())
pgport,backendport,frontport=ports
creation={'creationflags':getattr(subprocess,'CREATE_NO_WINDOW',0)} if os.name=='nt' else {'start_new_session':True}
def run(args,*,settings=None,cwd=ROOT,timeout=120,input=None,log=None):
    # En Windows postgres hereda handles del proceso de arranque; no mantener PIPE abierto.
    logfile=log or BASE/('command-'+str(time.monotonic_ns())+'.log')
    with logfile.open('w',encoding='utf-8') as stream:
        process=subprocess.Popen([str(a) for a in args],cwd=cwd,env=settings or env,stdin=subprocess.PIPE if input is not None else subprocess.DEVNULL,text=True,encoding='utf-8',errors='replace',stdout=stream,stderr=subprocess.STDOUT,**creation)
        try: process.communicate(input=input,timeout=timeout)
        except subprocess.TimeoutExpired:
            if os.name=='nt': subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            else:
                try: os.killpg(process.pid,signal.SIGKILL)
                except ProcessLookupError: pass
            process.wait(timeout=10)
            logfile.write_text(redact(logfile.read_text(encoding='utf-8',errors='replace')),encoding='utf-8')
            raise RuntimeError('Tiempo agotado; arbol de proceso propio detenido. Revisar '+str(logfile))
    output=redact(logfile.read_text(encoding='utf-8',errors='replace'))
    logfile.write_text(output,encoding='utf-8')
    if process.returncode: raise RuntimeError('Fallo de verificación; '+str(log or BASE)+'\n'+output[-8000:])
    return output
children=[]; handles=[]; started=False
password_file=BASE/'init-password.txt'
try:
    version=run([JAVA,'-version'],timeout=15)
    if not re.search(r'version "(?:2[3-9]|[3-9][0-9])',version): raise RuntimeError('Configura JAVA_HOME con el JDK24 acordado')
    # El empaquetado no arranca la aplicación ni conecta a una base real.
    maven=os.environ.get('MAVEN_CMD') or shutil.which('mvn.cmd') or shutil.which('mvn')
    if not maven: raise RuntimeError('Maven no disponible')
    run([maven,'-B','-ntp','-f','DataAnaliticsEnterprise/pom.xml','-DskipTests','package'],log=BASE/'package.log',timeout=180)
    password_file.write_text(ADMIN,encoding='utf-8')
    pg_env={**env,'PGPASSWORD':ADMIN}
    run([exe('initdb'),'-D',CLUSTER,'-U','tc10_admin','--auth=scram-sha-256','--pwfile',password_file,'--encoding=UTF8','--locale=C'],settings=pg_env,log=BASE/'initdb.log',timeout=60)
    password_file.unlink()
    run([exe('pg_ctl'),'-D',CLUSTER,'-l',BASE/'postgres.log','-o',f'-h 127.0.0.1 -p {pgport}','-w','start'],settings=pg_env,timeout=60); started=True
    run([exe('psql'),'-h','127.0.0.1','-p',pgport,'-U','tc10_admin','-d','postgres','-v','ON_ERROR_STOP=1'],
        settings=pg_env,input=f"CREATE ROLE tc10_app LOGIN NOSUPERUSER PASSWORD '{APP}';\nCREATE DATABASE tc10_browser OWNER tc10_app;\n",timeout=20)
    own_env={**env,'DB_URL':f'jdbc:postgresql://127.0.0.1:{pgport}/tc10_browser','DB_USER':'tc10_app','DB_PASSWORD':APP,
        'PORT':str(backendport),'SESSION_COOKIE_SECURE':'false','BOOTSTRAP_ADMIN_ENABLED':'true','BOOTSTRAP_ADMIN_EMAIL':EMAIL,
        'BOOTSTRAP_ADMIN_NAME':'Administrador de prueba TC10','BOOTSTRAP_ADMIN_PASSWORD':ROOT_PASSWORD,
        'INVITATION_MANUAL_DELIVERY_ENABLED':'true','LOGIN_IP_LIMIT':'1000','ACTIVATION_IP_LIMIT':'1000',
        'API_PROXY_TARGET':f'http://127.0.0.1:{backendport}','TC10_FRONTEND_URL':f'http://127.0.0.1:{frontport}',
        'TC10_ROOT_EMAIL':EMAIL,'TC10_ROOT_PASSWORD':ROOT_PASSWORD,'TC10_TEST_PASSWORD':TEST_PASSWORD}
    print('Iniciando JAR y proxy propios sobre PostgreSQL temporal.',flush=True)
    def launch(args,cwd,filename):
        out=(BASE/filename).open('w',encoding='utf-8'); handles.append(out)
        process=subprocess.Popen([str(a) for a in args],cwd=cwd,env=own_env,stdout=out,stderr=subprocess.STDOUT,**creation); children.append(process); return process
    backend=launch([JAVA,'-jar',ROOT/'DataAnaliticsEnterprise/target/DataAnaliticsEnterprise-1.0-SNAPSHOT.jar'],ROOT,'backend.log')
    deadline=time.monotonic()+75
    while True:
        if backend.poll() is not None: raise RuntimeError('El JAR terminó antes de estar disponible; revisar '+str(BASE/'backend.log'))
        try:
            sql="SELECT EXISTS(SELECT 1 FROM identidad.usuario WHERE normalized_email='"+EMAIL+"')"
            result=subprocess.run([str(exe('psql')),'-h','127.0.0.1','-p',str(pgport),'-U','tc10_app','-d','tc10_browser','-Atc',sql],env={**env,'PGPASSWORD':APP},capture_output=True,text=True,timeout=5,**creation)
            if result.returncode==0 and result.stdout.strip()=='t': break
        except subprocess.TimeoutExpired: pass
        if time.monotonic()>deadline: raise RuntimeError('La cuenta sintética de arranque no quedó disponible')
        time.sleep(.2)
    node=shutil.which('node')
    if not node: raise RuntimeError('Node no disponible')
    frontend=launch([node,ROOT/'Frontend/node_modules/vite/bin/vite.js','--host','127.0.0.1','--port',frontport],ROOT/'Frontend','vite.log')
    deadline=time.monotonic()+45
    while True:
        if frontend.poll() is not None: raise RuntimeError('Vite terminó antes de estar disponible')
        try:
            with urllib.request.urlopen(own_env['TC10_FRONTEND_URL'],timeout=2) as response:
                if response.status==200: break
        except Exception: pass
        if time.monotonic()>deadline: raise RuntimeError('El frontend temporal no quedó disponible')
        time.sleep(.2)
    if not own_env.get('PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH'):
        for candidate in [Path(r'C:\Program Files\Google\Chrome\Application\chrome.exe'),Path(r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe')]:
            if candidate.is_file(): own_env['PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH']=str(candidate); break
    result_dir=(ROOT/'Frontend/test-results').resolve()
    assert result_dir.is_relative_to((ROOT/'Frontend').resolve()) and not (ROOT/'Frontend/test-results').is_symlink()
    npm=shutil.which('npm.cmd') or shutil.which('npm')
    print('Ejecutando Chromium con cuentas sintéticas y sin interceptar la API.',flush=True)
    run([npm,'run','test:e2e:real'],settings=own_env,cwd=ROOT/'Frontend',timeout=180,log=BASE/'browser.log')
    result={'result':'PASS','api':'real','postgres_temporary':True,'cluster':str(CLUSTER),'frontend_url':own_env['TC10_FRONTEND_URL'],
        'backend_port':backendport,'credentials':'generated in process only','intercepted_api':False}
    (BASE/'verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('Recorrido real PASS. EVIDENCIA: '+str(BASE),flush=True)
finally:
    for child in reversed(children):
        if child.poll() is None:
            if os.name=='nt': subprocess.run(['taskkill','/PID',str(child.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            else:
                try: os.killpg(child.pid,signal.SIGTERM)
                except ProcessLookupError: pass
            try: child.wait(timeout=10)
            except subprocess.TimeoutExpired: child.kill();child.wait(timeout=5)
    for h in handles: h.close()
    # No conservar entradas de credenciales/códigos en evidencias de navegador fallidas.
    results=ROOT/'Frontend/test-results'
    if results.exists():
        for pattern in ['*.json','*.md','*.txt']:
            for file in results.rglob(pattern):
                text=file.read_text(encoding='utf-8',errors='replace'); file.write_text(redact(text),encoding='utf-8')
    for file in [BASE/'backend.log',BASE/'vite.log']:
        if file.exists(): file.write_text(redact(file.read_text(encoding='utf-8',errors='replace')),encoding='utf-8')
    if password_file.exists(): password_file.unlink()
    if started:
        run([exe('pg_ctl'),'-D',CLUSTER,'-m','fast','-w','stop'],settings={**env,'PGPASSWORD':ADMIN},timeout=30)
    print('JAR, Vite y PostgreSQL temporales detenidos; servicios existentes intactos.',flush=True)
