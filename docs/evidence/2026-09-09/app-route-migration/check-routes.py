from pathlib import Path
import urllib.request,urllib.error,json,sys,hashlib,subprocess
sys.excepthook=sys.__excepthook__
root=Path(__file__).resolve().parent
require_redirects='--static-only' not in sys.argv
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):return None
opener=urllib.request.build_opener(NoRedirect)
def request(path,method='GET'):
    req=urllib.request.Request('https://berthalla.io'+path,method=method,headers={'User-Agent':'Mozilla/5.0'})
    try:response=opener.open(req,timeout=40)
    except urllib.error.HTTPError as error:response=error
    with response:return response.status,{k.lower():v for k,v in response.headers.items()},response.read()
rows=[]
for path in ['/widget','/widget/','/widget/index.html','/widget?ref=legacy','/widget/?ref=legacy','/widget/index.html?ref=legacy','/app','/app?ref=legacy']:
    status,headers,body=request(path)
    if require_redirects or path.startswith('/app'):
        assert status==301,(path,status)
        expected='https://berthalla.io/app/'+('?ref=legacy' if '?' in path else '')
        assert headers['location']==expected,(path,headers.get('location'))
    rows.append({'path':path,'status':status,'location':headers.get('location')})
for path,file in [('/app/','website/app/index.html'),('/','website/index.html')]:
    status,headers,body=request(path);assert status==200
    assert body==(Path('/opt/berthalla')/file).read_bytes(),path
    if path=='/':assert b'href="/widget/"' not in body and body.count(b'href="/app/"')==5
    else:assert b'<link rel="canonical" href="https://berthalla.io/app/">' in body
    rows.append({'path':path,'status':status,'sha256':hashlib.sha256(body).hexdigest()})
for path in ['/widget/release.json?v=050','/widget/api/quote','/widget/v1/bert/quote']:
    status,headers,body=request(path);assert status==200,(path,status)
    data=json.loads(body)
    if 'release.json' in path:assert data['version']=='0.5.0' and data['sha256']=='c47fbee48bc37db5d0974fa453825f34288d9d9983a603033edafcc5040df4e0'
    else:assert data['meta']['freshness']=='fresh',data['meta']
    rows.append({'path':path,'status':status,'data':data})
for path in ['/widget/download/bert-widget.apk?sha=c47fbee4','/widget/download/bert-widget-0.5.0.apk?sha=c47fbee4']:
    status,headers,body=request(path,'HEAD');assert status==200,(path,status)
    assert headers.get('content-type')=='application/vnd.android.package-archive'
    assert 'attachment' in headers.get('content-disposition','')
    assert int(headers['content-length'])==29744436
    rows.append({'path':path,'status':status,'contentType':headers['content-type'],'length':headers['content-length']})
sha=subprocess.check_output(['git','-c','safe.directory=/opt/berthalla','-C','/opt/berthalla','rev-parse','HEAD'],text=True).strip()
(root/('route-results.json' if require_redirects else 'static-route-results.json')).write_text(json.dumps({'websiteSha':sha,'redirectsRequired':require_redirects,'rows':rows},indent=2)+'\n')
print(json.dumps({'websiteSha':sha,'redirectsRequired':require_redirects,'checkedRoutes':len(rows),'canonical':'https://berthalla.io/app/'}))
