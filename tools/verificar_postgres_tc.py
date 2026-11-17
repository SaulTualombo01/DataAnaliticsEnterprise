"""Verifica TC con PostgreSQL temporal aislado, sin tocar el servicio/bases instalados."""
from pathlib import Path
import json
import os
import secrets
import shutil
import socket
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
if (root/'DataAnaliticsEnterprise/Backend/src/main/java/org/dataanaliticsent/catalogo/CatalogoApi.java').exists():
    raise SystemExit('Para M1 usar tools/verificar_postgres.py, que prepara también el origen de datos de prueba.')
pg=Path(os.environ.get('PG_BIN',r'C:\Program Files\PostgreSQL\18\bin'))
base=Path(tempfile.mkdtemp(prefix='tic-tc-postgres-'))
cluster=base/'cluster'; password_file=base/'temporary-password.txt'
admin_password=secrets.token_urlsafe(32); app_password=secrets.token_urlsafe(32); source_password=secrets.token_urlsafe(32)
password_file.write_text(admin_password,encoding='utf-8')
env=dict(os.environ); env['PGPASSWORD']=admin_password
env['JAVA_HOME']=os.environ.get('JAVA_HOME',r'C:\Program Files\Java\jdk-24')
env.update(TIC_SECRETS_KEY_IDS='',TIC_SECRETS_ACTIVE_KEY_ID='',TIC_SECRETS_ALLOW_LOCAL_INSECURE='false')
env['PATH']=env['JAVA_HOME']+r'\bin;'+env['PATH']; env['BOOTSTRAP_ADMIN_ENABLED']='false'
with socket.socket() as sock: sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
def run(args,**kwargs):
    log=base/'postgres-command.log'
    with log.open('w',encoding='utf-8') as out: result=subprocess.run([str(a) for a in args],env=env,stdout=out,stderr=subprocess.STDOUT,**kwargs)
    if result.returncode: raise RuntimeError(log.read_text(encoding='utf-8',errors='replace'))
started=False
try:
    version=subprocess.run([str(Path(env['JAVA_HOME'])/'bin/javac.exe'),'-version'],capture_output=True,text=True,check=True).stdout.strip()
    if int(version.split()[1].split('.')[0])<23: raise RuntimeError('Configura JAVA_HOME con el JDK24 acordado antes de ejecutar el verificador')
    run([pg/'initdb.exe','-D',cluster,'-U','tic_test_admin','--auth=scram-sha-256','--pwfile',password_file,'--encoding=UTF8','--locale=C'],timeout=60)
    password_file.unlink()
    run([pg/'pg_ctl.exe','-D',cluster,'-l',base/'postgres.log','-o',f'-h 127.0.0.1 -p {port}','-w','start'],timeout=60); started=True
    run([pg/'psql.exe','-h','127.0.0.1','-p',str(port),'-U','tic_test_admin','-d','postgres','-v','ON_ERROR_STOP=1'],
        input=f"CREATE ROLE tic_test_app LOGIN NOSUPERUSER PASSWORD '{app_password}';\nCREATE DATABASE tic_tc_test OWNER tic_test_app;\nCREATE DATABASE tic_upgrade_test OWNER tic_test_app;\nCREATE ROLE tic_source_test LOGIN NOSUPERUSER PASSWORD '{source_password}';\nCREATE DATABASE tic_source_test OWNER tic_source_test;\n",text=True,timeout=20)
    env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/tic_tc_test',DB_USER='tic_test_app',DB_PASSWORD=app_password)
    env['DB_UPGRADE_URL']=f'jdbc:postgresql://127.0.0.1:{port}/tic_upgrade_test'
    env.update(TC11_SOURCE_URL=f'jdbc:postgresql://127.0.0.1:{port}/tic_source_test',TC11_SOURCE_USER='tic_source_test',TC11_SOURCE_PASSWORD=source_password)
    assert not ET.parse(root/'DataAnaliticsEnterprise/pom.xml').findall('.//{http://maven.apache.org/POM/4.0.0}build/{http://maven.apache.org/POM/4.0.0}directory')
    maven=os.environ.get('MAVEN_CMD') or shutil.which('mvn.cmd') or shutil.which('mvn')
    command=[maven,'-B','-ntp','-f','DataAnaliticsEnterprise/pom.xml','-Ppostgres-it','clean','verify']
    print('Verificando TC en un PostgreSQL temporal aislado.',flush=True)
    result=subprocess.run(command,cwd=root,env=env)
    if result.returncode: raise RuntimeError('La verificación Maven no pasó')
    suites=[ET.parse(path).getroot() for path in (root/'DataAnaliticsEnterprise/target/surefire-reports').glob('TEST-*.xml')]
    summary={'tests':sum(int(s.attrib['tests']) for s in suites),
        'failures':sum(int(s.attrib['failures']) for s in suites),'errors':sum(int(s.attrib['errors']) for s in suites),
        'skipped':sum(int(s.attrib['skipped']) for s in suites),'cluster':str(cluster),'temporary_only':True}
    (base/'verification.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print('EVIDENCIA: '+str(base),flush=True)
finally:
    if password_file.exists(): password_file.unlink()
    if started: run([pg/'pg_ctl.exe','-D',cluster,'-m','fast','-w','stop'],timeout=30)
    print('PostgreSQL temporal detenido; servicio instalado intacto.',flush=True)
