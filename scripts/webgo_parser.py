#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import asyncio, aiohttp, re, json, os, argparse, time, hashlib
from bs4 import BeautifulSoup

DB_FILE = "torrents.json"
STATE_KP = "enrich_state_kp.json"
STATE_TMDB = "enrich_state_tmdb.json"
CACHE_FILE = "api_cache.json"

SECTIONS = {1:"Зарубежное кино",4:"Зарубежные сериалы",5:"Наши фильмы",12:"Научно-популярные",16:"Наши сериалы",6:"Телевизор",7:"Мультипликация",10:"Аниме"}
BASE_LIST = "http://rutor.info/browse"
BASE_TOPIC = "http://rutor.info"
MIN_SEEDS_ADD = 10
MIN_LEECHES_ADD = 20
MAX_SEEDS_DELETE = 3
MAX_LEECHES_DELETE = 9
PAGES = int(os.getenv("PAGES","20"))
CONCURRENT_SECTIONS = 8
CONCURRENT_TOPICS = 20
HEADERS_WEB = {"User-Agent":"Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"}

KP_TOKEN = os.getenv("KP_TOKEN","")
KP_API_URL = "https://kinopoiskapiunofficial.tech/api/v2.2/films"
KP_STAFF_URL = "https://kinopoiskapiunofficial.tech/api/v1/staff"
HEADERS_KP = {"X-API-KEY":KP_TOKEN,"accept":"application/json"}
DELAY_KP = 0.3
KP_BATCH_SIZE = 10

TMDB_API_KEY = os.getenv("TMDB_API_KEY","")
TMDB_BASE = "https://api.themoviedb.org/3"
TMDB_BATCH_SIZE = 20
DELAY_TMDB = 0.1
MAX_ENRICH_PER_RUN = int(os.getenv("MAX_ENRICH_PER_RUN","0"))

def load_cache():
    if os.path.exists(CACHE_FILE):
        with open(CACHE_FILE,'r') as f: return json.load(f)
    return {}

def save_cache(cache):
    with open(CACHE_FILE,'w') as f: json.dump(cache,f,indent=2)

def cache_key(kp_id=None,imdb_id=None,title=None):
    if kp_id: return f"kp:{kp_id}"
    elif imdb_id: return f"imdb:{imdb_id}"
    elif title: return f"title:{hashlib.md5(title.encode()).hexdigest()[:16]}"
    return None

def load_db():
    if not os.path.exists(DB_FILE): return []
    with open(DB_FILE,'r',encoding='utf-8') as f: return json.load(f)

def save_db(items):
    with open(DB_FILE,'w',encoding='utf-8') as f: json.dump(items,f,ensure_ascii=False,indent=2)

def info_hash(magnet):
    m = re.search(r'btih:([a-fA-F0-9]+)',magnet)
    return m.group(1).lower() if m else ''

def parse_size(s):
    if not s or s=='?': return ''
    return s.strip()

def clean_title(raw_title):
    if not raw_title: return ''
    title = raw_title.strip()
    if ' / ' in title: title = title.split(' / ')[0].strip()
    title = re.sub(r'[\[\(]\d{4}[\]\)]','',title).strip()
    if '[' in title: title = title.split('[')[0].strip()
    if ' (S' in title: title = title.split(' (S')[0].strip()
    title = re.sub(r'\b(WEBRip|WEB-DL|HDTV|BDRip|BluRay|1080p|720p|2160p|4K|HDR|SDR|от\s+[^\s]+)\b.*','',title,flags=re.IGNORECASE).strip()
    return re.sub(r'\s+',' ',title).strip()

def load_state(state_file):
    if os.path.exists(state_file):
        with open(state_file,'r') as f: return json.load(f).get('last_id','')
    return ''

def save_state(state_file,last_id):
    with open(state_file,'w') as f: json.dump({'last_id':last_id},f)

async def fetch_page(session,url,retries=3):
    for attempt in range(retries):
        try:
            async with session.get(url,headers=HEADERS_WEB,timeout=aiohttp.ClientTimeout(total=20)) as resp:
                resp.raise_for_status()
                text = await resp.text()
                if 'magnet:' not in text and 'torrent' not in text:
                    await asyncio.sleep(1); continue
                if 'class="gai"' not in text and 'class="tum"' not in text:
                    await asyncio.sleep(1); continue
                return text
        except:
            await asyncio.sleep(1)
    return None

async def parse_topic(session,url):
    details = {}
    try:
        html = await fetch_page(session,url,retries=2)
        if not html: return details
        soup = BeautifulSoup(html,'html.parser')
        scope = soup.find('table', id='details') or soup.find('div', class_='movie-info') or soup
        imdb = scope.find('a', href=re.compile(r'imdb\.com/title/tt(\d+)'))
        if imdb:
            details['imdb_id'] = re.search(r'tt(\d+)', imdb['href']).group(1)
        kp = scope.find('a', href=re.compile(r'kinopoisk\.ru/film/(\d+)'))
        if kp:
            details['kp_id'] = re.search(r'/film/(\d+)', kp['href']).group(1)
    except:
        pass
    return details

async def process_section(session,cat_id,cat_name,existing_map):
    new_items = []
    sem = asyncio.Semaphore(CONCURRENT_TOPICS)
    db = load_db()
    for page in range(PAGES):
        url = f"{BASE_LIST}/{page}/{cat_id}/0/2"
        print(f"   📡 {url}")
        html = await fetch_page(session,url)
        if not html: continue
        soup = BeautifulSoup(html,'html.parser')
        rows = soup.select('tr.gai, tr.tum')
        if not rows: continue
        tasks = []
        for row in rows:
            try:
                mag_el = row.find('a',href=re.compile(r'magnet:'))
                if not mag_el: continue
                magnet = mag_el['href']
                ih = info_hash(magnet)
                if not ih: continue
                seeds = 0; leeches = 0
                try:
                    seeds_el = row.find('span',class_='green')
                    if seeds_el and seeds_el.text.strip().isdigit():
                        seeds = int(seeds_el.text.strip())
                except: pass
                try:
                    leech_el = row.find('span',class_='red')
                    if leech_el and leech_el.text.strip().isdigit():
                        leeches = int(leech_el.text.strip())
                except: pass
                if ih in existing_map:
                    idx = existing_map[ih]
                    record = db[idx]
                    record['seeds'] = seeds
                    record['leeches'] = leeches
                    record['_delete'] = (seeds<=MAX_SEEDS_DELETE and leeches<=MAX_LEECHES_DELETE)
                    continue
                if seeds < MIN_SEEDS_ADD and leeches < MIN_LEECHES_ADD:
                    continue
                title_el = row.find('a',href=re.compile(r'/torrent/'))
                title = title_el.text.strip() if title_el else '?'
                size_str = ''
                try:
                    cols = row.find_all('td')
                    if len(cols)>3 and not cols[3].has_attr('colspan'):
                        size_str = parse_size(cols[3].text.strip())
                except: pass
                topic_url = BASE_TOPIC + title_el['href'] if title_el and title_el.get('href') else ''
                async def bound(m=magnet,s=seeds,le=leeches,sz=size_str,tu=topic_url,cat=cat_name,t=title):
                    async with sem:
                        details = await parse_topic(session,tu) if tu else {}
                        imdb_id = details.get('imdb_id')
                        kp_id = details.get('kp_id')
                        if not imdb_id and not kp_id: return None
                        ym = re.search(r'\((\d{4})\)', t)
                        rutor_year = ym.group(1) if ym else ""
                        return {"title":t,"size":sz,"seeds":s,"magnet":m,"poster":"","rating":"","genre":"","year":"","url":tu,"category":cat,"leeches":le,"description":"","imdb_id":imdb_id or "","kp_id":kp_id or "","source":"RuTor","_rutor_year":rutor_year}
                tasks.append(bound())
            except Exception:
                pass
        if tasks:
            results = await asyncio.gather(*tasks)
            for item in results:
                if item and info_hash(item['magnet']) not in existing_map:
                    new_items.append(item)
                    existing_map[info_hash(item['magnet'])] = len(db)+len(new_items)-1
                    print(f"      ✅ {item['title'][:60]} ({item['seeds']} сидов)")
        await asyncio.sleep(0.1)
    return new_items

async def run_parser():
    print("📡 Парсинг rutor.info...")
    db = load_db()
    existing_map = {info_hash(it['magnet']):i for i,it in enumerate(db) if it.get('magnet')}
    async with aiohttp.ClientSession() as session:
        sem = asyncio.Semaphore(CONCURRENT_SECTIONS)
        async def worker(cid,cname):
            try:
                async with sem: return await process_section(session,cid,cname,existing_map)
            except Exception as e:
                print(f"   ❌ {cname}: {e}"); return 0
        results = await asyncio.gather(*[worker(cid,cname) for cid,cname in SECTIONS.items()])
    all_new = []
    for r in results:
        if isinstance(r, list): all_new.extend(r)
    db2 = load_db()
    exist = {info_hash(it['magnet']): i for i,it in enumerate(db2) if it.get('magnet')}
    for item in all_new:
        h = info_hash(item['magnet'])
        if h and h not in exist:
            db2.append(item); exist[h] = len(db2)-1
    save_db(db2)
    if all_new: print(f"🎉 Добавлено {len(all_new)}. Всего: {len(db2)}")
    else: print("ℹ️ Новых нет. Сиды обновлены, мёртвые удалены.")

async def fetch_kp_batch(session,kp_ids):
    sem = asyncio.Semaphore(KP_BATCH_SIZE)
    cache = load_cache()
    async def fetch_one(kp_id):
        async with sem:
            key = cache_key(kp_id=kp_id)
            if key in cache: return kp_id,cache[key]
            try:
                url = f"{KP_API_URL}/{kp_id}"
                async with session.get(url,headers=HEADERS_KP,timeout=10) as resp:
                    if resp.status != 200: return kp_id,None
                    data = await resp.json()
                    staff_data = None
                    try:
                        async with session.get(f"{KP_STAFF_URL}?filmId={kp_id}",headers=HEADERS_KP,timeout=10) as sr:
                            if sr.status == 200: staff_data = await sr.json()
                    except: pass
                    fields = {}
                    if data.get('nameRu'): fields['title'] = data['nameRu'] or data.get('nameOriginal')
                    if data.get('year'): fields['year'] = str(data['year'])
                    if data.get('ratingKinopoisk'): fields['rating'] = float(data['ratingKinopoisk'])
                    if data.get('posterUrl'): fields['poster'] = data['posterUrl']
                    if data.get('genres'): fields['genre'] = ', '.join([g['genre'] for g in data['genres']])
                    if data.get('description'): fields['description'] = data['description']
                    if data.get('slogan'): fields['slogan'] = data['slogan']
                    if data.get('filmLength'): fields['duration'] = int(data['filmLength'])
                    if data.get('ratingAgeLimits'): fields['age_rating'] = str(data['ratingAgeLimits'])
                    if data.get('imdbId'): fields['imdb_id'] = str(data['imdbId'])
                    if data.get('countries'): fields['countries'] = ', '.join([c['country'] for c in data['countries']])
                    if staff_data:
                        director = None; actors = []
                        for p in staff_data:
                            if p.get('professionKey') == 'DIRECTOR': director = p.get('nameRu') or p.get('nameEn')
                            elif p.get('professionKey') == 'ACTOR': actors.append(p.get('nameRu') or p.get('nameEn'))
                        if director: fields['director'] = director
                        if actors: fields['actors'] = ', '.join(actors[:5])
                    cache[key] = fields
                    return kp_id,fields
            except: return kp_id,None
    results = await asyncio.gather(*[fetch_one(k) for k in kp_ids])
    save_cache(cache)
    return {k:d for k,d in results if d}

async def run_enrich_kp():
    print("🔄 KP обогащение...")
    start = time.time()
    movies = load_db()
    need = {}
    for m in movies:
        kp = m.get('kp_id')
        if not kp: continue
        need.setdefault(kp,[]).append(m)
    pending = [kp for kp,g in need.items() if any(not m.get('genre') or not m.get('poster') or not m.get('director') for m in g)]
    if MAX_ENRICH_PER_RUN > 0: pending = pending[:MAX_ENRICH_PER_RUN]
    total = len(pending)
    if total == 0: print("ℹ️ Всё обогащено KP."); return
    print(f"📊 KP: {total} (пакеты по {KP_BATCH_SIZE})")
    start_id = load_state(STATE_KP); idx = 0
    if start_id and start_id in pending: idx = pending.index(start_id)
    enriched = 0
    async with aiohttp.ClientSession() as session:
        for i in range(idx,total,KP_BATCH_SIZE):
            batch = pending[i:i+KP_BATCH_SIZE]
            print(f"   📦 {i//KP_BATCH_SIZE+1}/{(total+KP_BATCH_SIZE-1)//KP_BATCH_SIZE}")
            results = await fetch_kp_batch(session,batch)
            for kp_id,fields in results.items():
                if kp_id in need:
                    updated = 0
                    kp_year = fields.get('year', '')
                    for m in need[kp_id]:
                        rutor_year = m.get('_rutor_year', m.get('year', ''))
                        if kp_year and rutor_year:
                            try:
                                if abs(int(kp_year) - int(rutor_year)) > 2:
                                    continue
                            except: pass
                        for k,v in fields.items():
                            if v and not m.get(k): m[k]=v; updated+=1
                    if updated: enriched += 1
            if i+len(batch) < total:
                save_state(STATE_KP,batch[-1]); save_db(movies)
                print(f"   💾 {i+len(batch)}/{total}")
            await asyncio.sleep(DELAY_KP)
    save_state(STATE_KP,pending[-1] if pending else '')
    save_db(movies)
    print(f"🎉 KP: {enriched} за {time.time()-start:.1f}s")

async def fetch_tmdb_batch(session,items):
    sem = asyncio.Semaphore(TMDB_BATCH_SIZE)
    cache = load_cache()
    async def fetch_one(gid,imdb_id,title):
        async with sem:
            key = cache_key(imdb_id=imdb_id) if imdb_id else cache_key(title=title)
            if key and key in cache: return gid,cache[key]
            try:
                tmdb_id = None
                if imdb_id:
                    async with session.get(f"{TMDB_BASE}/find/{imdb_id}?external_source=imdb_id&language=ru&api_key={TMDB_API_KEY}",timeout=10) as r:
                        if r.status==200:
                            d = await r.json()
                            if d.get('movie_results'): tmdb_id = d['movie_results'][0]['id']
                if not tmdb_id and title:
                    clean = clean_title(title)
                    if clean:
                        async with session.get(f"{TMDB_BASE}/search/multi?query={clean}&language=ru&api_key={TMDB_API_KEY}",timeout=10) as r:
                            if r.status==200:
                                d = await r.json()
                                if d.get('results'): tmdb_id = d['results'][0]['id']
                if not tmdb_id: return gid,None
                data = None
                for mt in ('movie','tv'):
                    async with session.get(f"{TMDB_BASE}/{mt}/{tmdb_id}?language=ru&append_to_response=credits&api_key={TMDB_API_KEY}",timeout=10) as r:
                        if r.status==200: data = await r.json(); break
                if not data: return gid,None
                fields = {}
                if data.get('title') or data.get('name'): fields['title'] = data.get('title') or data.get('name')
                date = data.get('release_date') or data.get('first_air_date')
                if date: fields['year'] = date[:4]
                if data.get('vote_average'): fields['rating'] = float(data['vote_average'])
                if data.get('poster_path'): fields['poster'] = f"https://image.tmdb.org/t/p/w500{data['poster_path']}"
                if data.get('genres'): fields['genre'] = ', '.join([g['name'] for g in data['genres']])
                if data.get('overview'): fields['description'] = data['overview']
                if data.get('tagline'): fields['slogan'] = data['tagline']
                rt = data.get('runtime') or (data.get('episode_run_time',[0])[0] if isinstance(data.get('episode_run_time'),list) and data['episode_run_time'] else None)
                if rt: fields['duration'] = rt
                if data.get('imdb_id'): fields['imdb_id'] = data['imdb_id']
                if data.get('production_countries'): fields['countries'] = ', '.join([c['name'] for c in data['production_countries']])
                credits = data.get('credits',{})
                if credits:
                    dirs = [c['name'] for c in credits.get('crew',[]) if c.get('job')=='Director']
                    if dirs: fields['director'] = ', '.join(dirs[:2])
                    acts = [a['name'] for a in credits.get('cast',[])[:5]]
                    if acts: fields['actors'] = ', '.join(acts)
                if key: cache[key] = fields
                return gid,fields
            except: return gid,None
    results = await asyncio.gather(*[fetch_one(g,i,t) for g,i,t in items])
    save_cache(cache)
    return {g:d for g,d in results if d}

async def run_enrich_tmdb():
    print("🔄 TMDB обогащение...")
    start = time.time()
    movies = load_db()
    groups = {}
    for m in movies:
        gid = m.get('imdb_id') or clean_title(m.get('title',''))
        if not gid: continue
        groups.setdefault(gid,[]).append(m)
    pending = []
    for gid,group in groups.items():
        imdb = next((m.get('imdb_id') for m in group if m.get('imdb_id')),None)
        title = next((m.get('title') for m in group if m.get('title')),None)
        if imdb or title:
            if any(not m.get('description') or not m.get('actors') for m in group):
                pending.append((gid,imdb,title))
    if MAX_ENRICH_PER_RUN > 0: pending = pending[:MAX_ENRICH_PER_RUN]
    total = len(pending)
    if total == 0: print("ℹ️ Всё обогащено TMDB."); return
    print(f"📊 TMDB: {total} (пакеты по {TMDB_BATCH_SIZE})")
    start_id = load_state(STATE_TMDB); idx = 0
    if start_id:
        for i,(gid,_,_) in enumerate(pending):
            if gid == start_id: idx = i; break
    enriched = 0
    async with aiohttp.ClientSession() as session:
        for i in range(idx,total,TMDB_BATCH_SIZE):
            batch = pending[i:i+TMDB_BATCH_SIZE]
            print(f"   📦 {i//TMDB_BATCH_SIZE+1}/{(total+TMDB_BATCH_SIZE-1)//TMDB_BATCH_SIZE}")
            results = await fetch_tmdb_batch(session,batch)
            for gid,fields in results.items():
                if gid in groups:
                    updated = 0
                    tmdb_year = fields.get('year', '')
                    for m in groups[gid]:
                        rutor_year = m.get('_rutor_year', m.get('year', ''))
                        if tmdb_year and rutor_year:
                            try:
                                if abs(int(tmdb_year) - int(rutor_year)) > 2:
                                    continue
                            except: pass
                        for k,v in fields.items():
                            if v and not m.get(k): m[k]=v; updated+=1
                    if updated: enriched += 1
            if i+len(batch) < total:
                save_state(STATE_TMDB,batch[-1][0]); save_db(movies)
                print(f"   💾 {i+len(batch)}/{total}")
            await asyncio.sleep(DELAY_TMDB)
    save_state(STATE_TMDB,pending[-1][0] if pending else '')
    save_db(movies)
    print(f"🎉 TMDB: {enriched} за {time.time()-start:.1f}s")

async def download_posters_step():
    print("🖼️ Скачивание постеров...")
    start = time.time()
    movies = load_db()
    if not movies:
        print("ℹ️ База пуста"); return
    os.makedirs("posters", exist_ok=True)
    need = [m for m in movies if m.get('poster') and not m['poster'].startswith('posters/')]
    print(f"📊 Постеров к скачиванию: {len(need)}")
    sem = asyncio.Semaphore(30)
    downloaded = 0
    async def dl(session, m):
        nonlocal downloaded
        poster = m.get('poster', '')
        h = info_hash(m.get('magnet', ''))
        if not h: return
        fname = f"posters/{h}.jpg"
        if os.path.exists(fname) and os.path.getsize(fname) > 1000:
            m['poster'] = fname; return
        async with sem:
            try:
                async with session.get(poster, timeout=20) as r:
                    if r.status == 200:
                        data = await r.read()
                        if len(data) > 1000:
                            with open(fname, 'wb') as fh:
                                fh.write(data)
                            m['poster'] = fname
                            downloaded += 1
            except: pass
    async with aiohttp.ClientSession() as session:
        B = 200
        for i in range(0, len(need), B):
            batch = need[i:i+B]
            await asyncio.gather(*[dl(session, m) for m in batch])
            print(f"   📦 {i+len(batch)}/{len(need)}")
    save_db(movies)
    print(f"🎉 Постеров скачано: {downloaded} за {time.time()-start:.1f}s")

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--mode',choices=['parse','kp','tmdb','all','fast'],default='all')
    args = p.parse_args()
    if not KP_TOKEN: print("⚠️ KP_TOKEN не задан, KP пропущен")
    if not TMDB_API_KEY: print("⚠️ TMDB_API_KEY не задан, TMDB пропущен")
    if args.mode in ('all','fast'):
        asyncio.run(run_parser())
        if KP_TOKEN: asyncio.run(run_enrich_kp())
        if TMDB_API_KEY: asyncio.run(run_enrich_tmdb())
        asyncio.run(download_posters_step())
    elif args.mode == 'parse': asyncio.run(run_parser())
    elif args.mode == 'kp' and KP_TOKEN: asyncio.run(run_enrich_kp())
    elif args.mode == 'tmdb' and TMDB_API_KEY: asyncio.run(run_enrich_tmdb())

if __name__ == '__main__':
    main()