#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import asyncio, aiohttp, json, os, sys
from io import BytesIO
from datetime import datetime, timezone
from PIL import Image

TMDB_API_KEY = os.getenv("TMDB_API_KEY", "")
TMDB_BASE = "https://api.themoviedb.org/3"
MOVIES_PER_GENRE = 100
POSTERS_DIR = "posters_tmdb"
ACTORS_DIR = "posters_actors"
JSON_FILE = "tmdb_movies.json"

YEARS = [2023, 2024, 2025, 2026]

ACTOR_MIN_YEAR = 1980
ACTOR_FILMS_LIMIT = 30
ACTOR_MIN_MOVIES_ORIGIN = 2   # для origin-кандидатов
TOP_ACTORS_LIMIT = 800        # после фильтра на фото
POPULAR_PEOPLE_PAGES = 20     # 1000 popular

# ═══ 150 curated легенд ═══
LEGEND_NAMES = [
    # Экшен 80-90х (40)
    "Jean-Claude Van Damme", "Arnold Schwarzenegger", "Sylvester Stallone",
    "Bruce Willis", "Dolph Lundgren", "Steven Seagal", "Jackie Chan",
    "Chuck Norris", "Jet Li", "Jean Reno", "Mel Gibson", "Danny Glover",
    "Kurt Russell", "Wesley Snipes", "Michael Douglas", "Chow Yun-fat",
    "Sammo Hung", "Donnie Yen", "Michelle Yeoh", "Cynthia Rothrock",
    "Sigourney Weaver", "Linda Hamilton", "Milla Jovovich", "Nicolas Cage",
    "John Travolta", "Christian Slater", "Keanu Reeves", "Tommy Lee Jones",
    "Pierce Brosnan", "Sean Connery", "Roger Moore", "Timothy Dalton",
    "Harrison Ford", "Mark Hamill", "Carrie Fisher", "Patrick Swayze",
    "Kevin Costner", "Denzel Washington", "Tom Cruise", "Alec Baldwin",
    # Драма (30)
    "Robert De Niro", "Al Pacino", "Joe Pesci", "Anthony Hopkins",
    "Morgan Freeman", "Tom Hanks", "Jack Nicholson", "Dustin Hoffman",
    "Robin Williams", "Brad Pitt", "Edward Norton", "Kevin Spacey",
    "Russell Crowe", "Hugh Jackman", "Christian Bale", "Gary Oldman",
    "Daniel Day-Lewis", "Liam Neeson", "Ralph Fiennes", "Ben Kingsley",
    "Ian McKellen", "Michael Caine", "Judi Dench", "Meryl Streep",
    "Cate Blanchett", "Nicole Kidman", "Julia Roberts", "Jodie Foster",
    "Glenn Close", "Kathy Bates",
    # Комедия (20)
    "Jim Carrey", "Adam Sandler", "Mike Myers", "Eddie Murphy",
    "Steve Martin", "Bill Murray", "Rowan Atkinson", "Ben Stiller",
    "Will Ferrell", "Jack Black", "John Cleese", "Chevy Chase",
    "Dan Aykroyd", "Rick Moranis", "Steve Carell", "Kevin Hart",
    "Sacha Baron Cohen", "Russell Brand", "Zach Galifianakis", "Owen Wilson",
    # Современные (30)
    "Leonardo DiCaprio", "Cillian Murphy", "Matt Damon", "Ben Affleck",
    "Johnny Depp", "Ryan Reynolds", "Ryan Gosling", "Chris Evans",
    "Chris Hemsworth", "Robert Downey Jr.", "Mark Ruffalo", "Jeremy Renner",
    "Scarlett Johansson", "Chris Pratt", "Dwayne Johnson", "Vin Diesel",
    "Jason Statham", "Tom Hardy", "Jake Gyllenhaal", "Michael Fassbender",
    "Idris Elba", "Benedict Cumberbatch", "Tom Hiddleston", "Oscar Isaac",
    "Pedro Pascal", "Paul Rudd", "Will Smith", "Samuel L. Jackson",
    "Timothée Chalamet", "Joaquin Phoenix",
    # Актрисы (30)
    "Angelina Jolie", "Natalie Portman", "Kate Winslet", "Kristen Stewart",
    "Jennifer Lawrence", "Anne Hathaway", "Emma Stone", "Emma Watson",
    "Charlize Theron", "Uma Thurman", "Cameron Diaz", "Drew Barrymore",
    "Halle Berry", "Zoe Saldana", "Karen Gillan", "Elizabeth Olsen",
    "Brie Larson", "Gal Gadot", "Margot Robbie", "Florence Pugh",
    "Anya Taylor-Joy", "Jenna Ortega", "Millie Bobby Brown", "Zendaya",
    "Saoirse Ronan", "Sydney Sweeney", "Daisy Ridley", "Lupita Nyong'o",
    "Danai Gurira", "Tilda Swinton",
]

GENRES = [
    {"id": 28,    "name": "Боевик",         "emoji": "💥"},
    {"id": 35,    "name": "Комедия",        "emoji": "😂"},
    {"id": 878,   "name": "Фантастика",     "emoji": "🚀"},
    {"id": 27,    "name": "Ужасы",          "emoji": "👻"},
    {"id": 18,    "name": "Драма",          "emoji": "🎭"},
    {"id": 9648,  "name": "Детектив",       "emoji": "🕵️"},
    {"id": 10749, "name": "Мелодрама",      "emoji": "💕"},
    {"id": 12,    "name": "Приключения",    "emoji": "🗺️"},
    {"id": 14,    "name": "Фэнтези",        "emoji": "🏰"},
    {"id": 10752, "name": "Военный",        "emoji": "⚔️"},
    {"id": 16,    "name": "Мультфильм",     "emoji": "🎨"},
    {"id": 36,    "name": "Исторический",   "emoji": "📜"},
    {"id": 80,    "name": "Криминал",       "emoji": "🔫"},
    {"id": 99,    "name": "Документальный", "emoji": "📽️"},
    {"id": 10751, "name": "Семейный",       "emoji": "👨‍👩‍👧"},
    {"id": 10402, "name": "Музыка",         "emoji": "🎵"},
    {"id": 53,    "name": "Триллер",        "emoji": "🔪"},
    {"id": 37,    "name": "Вестерн",        "emoji": "🤠"},
]

VOICE_KEYWORDS = ("voice", "голос", "озвуч", "дубляж", "narrator", "(voice)", "voice-over")

def is_voice_role(character):
    if not character: return False
    cl = character.lower()
    return any(kw in cl for kw in VOICE_KEYWORDS)

async def fetch_discover(session, genre_id):
    out = []
    for year in YEARS:
        for page in (1, 2, 3):
            url = (f"{TMDB_BASE}/discover/movie?with_genres={genre_id}"
                   f"&sort_by=popularity.desc&primary_release_year={year}"
                   f"&vote_count.gte=20&language=ru-RU&page={page}&api_key={TMDB_API_KEY}")
            try:
                async with session.get(url, timeout=15) as r:
                    if r.status != 200: continue
                    data = await r.json()
                    results = data.get("results", [])
                    out.extend(results)
                    if len(results) < 20: break
                    if len(out) >= MOVIES_PER_GENRE * 2: return out[:MOVIES_PER_GENRE]
            except Exception as e:
                print(f"  discover err {genre_id} {year} p{page}: {e}")
        if len(out) >= MOVIES_PER_GENRE * 2: break
    return out[:MOVIES_PER_GENRE]

async def fetch_details(session, movie_id):
    url = (f"{TMDB_BASE}/movie/{movie_id}?language=ru-RU"
           f"&append_to_response=credits,keywords,external_ids&api_key={TMDB_API_KEY}")
    try:
        async with session.get(url, timeout=20) as r:
            if r.status != 200: return None
            data = await r.json()
            credits = data.get("credits", {})
            cast_ids = [c.get("id", 0) for c in credits.get("cast", []) if c.get("id", 0) > 0]
            cast_details = []
            for c in credits.get("cast", [])[:15]:
                cast_details.append({
                    "id": c.get("id", 0),
                    "name": c.get("name", ""),
                    "profile_path": c.get("profile_path") or "",
                    "character": c.get("character", "") or "",
                    "order": c.get("order", 99),
                })
            crew = []
            for c in credits.get("crew", []):
                if c.get("job") not in ("Director", "Producer", "Composer", "Screenplay", "Writer"): continue
                crew.append({
                    "id": c.get("id", 0),
                    "name": c.get("name", ""),
                    "job": c.get("job", ""),
                    "department": c.get("department", ""),
                })
            companies = []
            for c in data.get("production_companies", []):
                companies.append({
                    "id": c.get("id", 0),
                    "name": c.get("name", ""),
                    "logo_path": c.get("logo_path") or "",
                })
            coll = None
            if data.get("belongs_to_collection"):
                bc = data["belongs_to_collection"]
                coll = {
                    "id": bc.get("id", 0),
                    "name": bc.get("name", ""),
                    "poster_path": bc.get("poster_path") or "",
                }
            keywords = [k.get("name", "") for k in data.get("keywords", {}).get("keywords", [])]
            director = ""
            for c in crew:
                if c["job"] == "Director":
                    director = c["name"]; break
            external = data.get("external_ids", {})
            return {
                "runtime": data.get("runtime", 0) or 0,
                "director": director,
                "cast": [c["name"] for c in cast_details],
                "cast_ids": cast_ids,
                "cast_details": cast_details,
                "crew": crew,
                "genres": [g["name"] for g in data.get("genres", [])],
                "production_companies": companies,
                "belongs_to_collection": coll,
                "budget": data.get("budget", 0) or 0,
                "revenue": data.get("revenue", 0) or 0,
                "tagline": data.get("tagline", "") or "",
                "original_language": data.get("original_language", ""),
                "spoken_languages": [sl.get("name", "") for sl in data.get("spoken_languages", [])],
                "keywords": keywords,
                "imdb_id": external.get("imdb_id", "") or "",
            }
    except Exception as e:
        print(f"  details err {movie_id}: {e}")
        return None

async def search_person(session, name):
    """Возвращает (id, name, profile_path) первого совпадения или None."""
    enc = name.replace(" ", "%20")
    url = f"{TMDB_BASE}/search/person?query={enc}&language=en-US&page=1&api_key={TMDB_API_KEY}"
    try:
        async with session.get(url, timeout=15) as r:
            if r.status != 200: return None
            data = await r.json()
            results = data.get("results", [])
            if not results: return None
            p = results[0]
            return {
                "id": p.get("id", 0),
                "name": p.get("name", name),
                "profile_path": p.get("profile_path") or "",
            }
    except Exception as e:
        print(f"  search_person err {name}: {e}")
        return None

async def fetch_person_credits(session, person_id):
    """Полная фильмография: cast-only, 1980+, не voice, sorted by rating."""
    url = f"{TMDB_BASE}/person/{person_id}/movie_credits?language=ru-RU&api_key={TMDB_API_KEY}"
    try:
        async with session.get(url, timeout=25) as r:
            if r.status != 200: return []
            data = await r.json()
            cast = data.get("cast", [])
            filtered = []
            for c in cast:
                release = c.get("release_date") or ""
                y_str = release[:4] if release else ""
                try:
                    y = int(y_str)
                except (ValueError, TypeError):
                    continue
                if y < ACTOR_MIN_YEAR: continue
                if is_voice_role(c.get("character") or ""): continue
                if not c.get("poster_path"): continue
                filtered.append(c)
            filtered.sort(key=lambda c: (-(c.get("vote_average") or 0), -(c.get("vote_count") or 0)))
            return filtered[:ACTOR_FILMS_LIMIT]
    except Exception as e:
        print(f"  person credits err {person_id}: {e}")
        return []

async def download_poster(session, poster_path, movie_id):
    if not poster_path: return False
    url = f"https://image.tmdb.org/t/p/w500{poster_path}"
    try:
        async with session.get(url, timeout=25) as r:
            if r.status != 200: return False
            content = await r.read()
        img = Image.open(BytesIO(content)).convert("RGB")
        img.thumbnail((400, 600), Image.LANCZOS)
        img.save(f"{POSTERS_DIR}/{movie_id}.webp", "WEBP", quality=78, method=6)
        return True
    except Exception as e:
        return False

async def download_actor_photo(session, profile_path, person_id):
    if not profile_path: return False
    url = f"https://image.tmdb.org/t/p/w185{profile_path}"
    try:
        async with session.get(url, timeout=20) as r:
            if r.status != 200: return False
            content = await r.read()
        img = Image.open(BytesIO(content)).convert("RGB")
        img.thumbnail((185, 278), Image.LANCZOS)
        img.save(f"{ACTORS_DIR}/{person_id}.webp", "WEBP", quality=78, method=6)
        return True
    except Exception as e:
        return False

async def fetch_popular_people(session):
    """Возвращает dict{id: {id, name, popularity, profile_path}}."""
    out = {}
    for page in range(1, POPULAR_PEOPLE_PAGES + 1):
        url = f"{TMDB_BASE}/person/popular?language=en-US&page={page}&api_key={TMDB_API_KEY}"
        try:
            async with session.get(url, timeout=15) as r:
                if r.status != 200: continue
                data = await r.json()
                for p in data.get("results", []):
                    pid = p.get("id", 0)
                    if pid > 0 and pid not in out:
                        out[pid] = {
                            "id": pid,
                            "name": p.get("name", ""),
                            "popularity": float(p.get("popularity", 0) or 0),
                            "profile_path": p.get("profile_path") or "",
                        }
        except Exception as e:
            print(f"  popular err p{page}: {e}")
    return out

async def main():
    if not TMDB_API_KEY:
        print("ERROR: TMDB_API_KEY not set"); sys.exit(1)
    os.makedirs(POSTERS_DIR, exist_ok=True)
    os.makedirs(ACTORS_DIR, exist_ok=True)
    all_movies = {}
    t_start = datetime.now()

    async with aiohttp.ClientSession() as session:
        # ═══ 1. Discover origin ═══
        print("🔍 [1/10] Discover 18 жанров × 4 года...")
        for g in GENRES:
            results = await fetch_discover(session, g["id"])
            print(f"  {g['name']}: {len(results)}")
            for m in results:
                if m["id"] not in all_movies:
                    all_movies[m["id"]] = m
        print(f"📊 Origin фильмов: {len(all_movies)}")

        # ═══ 2. Details origin ═══
        print("📥 [2/10] Details origin...")
        sem = asyncio.Semaphore(15)
        async def enrich(mid):
            async with sem:
                d = await fetch_details(session, mid)
                if d: all_movies[mid]["_details"] = d
        await asyncio.gather(*[enrich(mid) for mid in list(all_movies.keys())])

        # ═══ 3. Постеры origin ═══
        print("🖼️ [3/10] Постеры origin...")
        sem2 = asyncio.Semaphore(12)
        async def dl(mid):
            async with sem2:
                return mid, await download_poster(session, all_movies[mid].get("poster_path"), mid)
        results = await asyncio.gather(*[dl(mid) for mid in list(all_movies.keys())])
        poster_ok = dict(results)
        ok_count = sum(1 for v in poster_ok.values() if v)
        print(f"✅ Постеров origin: {ok_count}/{len(all_movies)}")

        # ═══ 4. Собираем кандидатов ═══
        print("🎭 [4/10] Сбор кандидатов...")
        candidates = {}  # id -> {id, name, profile_path, origin_in_popular, popularity}

        # 4a. Legend whitelist через /search/person
        print(f"  Whitelist: {len(LEGEND_NAMES)} имён через /search/person...")
        sem_legend = asyncio.Semaphore(10)
        async def find_legend(name):
            async with sem_legend:
                return await search_person(session, name)
        legend_results = await asyncio.gather(*[find_legend(n) for n in LEGEND_NAMES])
        for p in legend_results:
            if p and p["id"] > 0:
                candidates[p["id"]] = {
                    "id": p["id"], "name": p["name"], "profile_path": p["profile_path"],
                    "is_legend": True, "is_popular": False, "popularity": 0.0,
                }
        print(f"    → найдено в whitelist: {len(candidates)}")

        # 4b. Popular (1000)
        print(f"  Popular: {POPULAR_PEOPLE_PAGES} страниц...")
        popular = await fetch_popular_people(session)
        print(f"    → popular: {len(popular)}")
        for pid, p in popular.items():
            if pid not in candidates:
                candidates[pid] = {
                    "id": pid, "name": p["name"], "profile_path": p["profile_path"],
                    "is_legend": False, "is_popular": True, "popularity": p["popularity"],
                }
            else:
                candidates[pid]["is_popular"] = True
                candidates[pid]["popularity"] = p["popularity"]

        # 4c. Актёры из origin (movie_count>=2)
        print(f"  Origin-актёры (movie_count>={ACTOR_MIN_MOVIES_ORIGIN})...")
        origin_actor_count = {}
        for mid, m in all_movies.items():
            d = m.get("_details", {})
            if not d: continue
            for c in d.get("cast_details", []):
                aid = c.get("id", 0)
                if aid == 0: continue
                origin_actor_count[aid] = origin_actor_count.get(aid, 0) + 1
        added_origin = 0
        for aid, count in origin_actor_count.items():
            if count < ACTOR_MIN_MOVIES_ORIGIN: continue
            if aid in candidates: continue
            # имя из cast_details
            name, prof = "", ""
            for mid, m in all_movies.items():
                d = m.get("_details", {})
                if not d: continue
                for c in d.get("cast_details", []):
                    if c.get("id") == aid:
                        name, prof = c.get("name", ""), c.get("profile_path", "")
                        break
                if name: break
            if not name: continue
            candidates[aid] = {
                "id": aid, "name": name, "profile_path": prof,
                "is_legend": False, "is_popular": False, "popularity": 0.0,
            }
            added_origin += 1
        print(f"    → добавлено origin: {added_origin}")
        print(f"📊 Всего кандидатов: {len(candidates)}")

        # ═══ 5. Фильмографии ВСЕХ кандидатов ═══
        print(f"🎬 [5/10] Фильмографии {len(candidates)} кандидатов (1980+, cast-only, no voice)...")
        credits_by_actor = {}
        sem_cred = asyncio.Semaphore(20)
        async def fetch_cred(aid):
            async with sem_cred:
                credits_by_actor[aid] = await fetch_person_credits(session, aid)
        await asyncio.gather(*[fetch_cred(aid) for aid in candidates.keys()])

        # ═══ 6. Пересчёт score по РЕАЛЬНОЙ фильмографии ═══
        print("📊 [6/10] Пересчёт score по фильмографии...")
        valid_candidates = []
        for aid, c in candidates.items():
            credits = credits_by_actor.get(aid, [])
            if not credits: continue
            # профиль должен быть (из origin / popular / search)
            if not c["profile_path"]: continue
            ratings = [float(cr.get("vote_average") or 0) for cr in credits if (cr.get("vote_average") or 0) > 0]
            votes = [int(cr.get("vote_count") or 0) for cr in credits if (cr.get("vote_count") or 0) > 0]
            avg_r = sum(ratings) / len(ratings) if ratings else 0
            votes_total = sum(votes)
            valid_candidates.append({
                "id": aid,
                "name": c["name"],
                "profile_path": c["profile_path"],
                "movie_count": len(credits),
                "avg_rating": round(avg_r, 1),
                "votes_total": votes_total,
                "is_legend": c["is_legend"],
                "is_popular": c["is_popular"],
                "popularity": c["popularity"],
            })
        print(f"    → с фильмографией и фото: {len(valid_candidates)}")

        # Score: movie_count*0.4 + avg_rating*0.3 + votes_total*0.3 + boost легенд/popular
        max_count = max((c["movie_count"] for c in valid_candidates), default=1)
        for c in valid_candidates:
            c["score"] = round(
                (c["movie_count"] / max_count) * 100 * 0.4
                + c["avg_rating"] * 0.3
                + min(c["votes_total"] / 10000.0, 50) * 0.3
                + (15 if c["is_legend"] else 0)
                + (10 if c["is_popular"] else 0),
                2
            )

        # ═══ 7. Top-800 ═══
        top_actors = sorted(valid_candidates, key=lambda x: -x["score"])[:TOP_ACTORS_LIMIT]
        print(f"🎭 [7/10] Top-{len(top_actors)} актёров отобрано")

        # ═══ 8. Фото top-800 ═══
        print(f"📸 [8/10] Фото {len(top_actors)} актёров (w185)...")
        sem3 = asyncio.Semaphore(10)
        async def dl_actor(a):
            async with sem3:
                return a["id"], await download_actor_photo(session, a["profile_path"], a["id"])
        actor_results = await asyncio.gather(*[dl_actor(a) for a in top_actors])
        actor_ok = dict(actor_results)
        for a in top_actors:
            a["profile_file"] = f"{a['id']}.webp" if actor_ok.get(a["id"]) else ""
        top_actors = [a for a in top_actors if a["profile_file"]]
        actor_ok_count = len(top_actors)
        print(f"✅ Фото: {actor_ok_count}")

        # ═══ 9. Новые фильмы из фильмографий ═══
        print("🆕 [9/10] Новые фильмы из фильмографий...")
        top_actor_ids = {a["id"] for a in top_actors}
        new_ids = set()
        for aid in top_actor_ids:
            credits = credits_by_actor.get(aid, [])
            for c in credits:
                mid = c.get("id")
                if mid and mid not in all_movies:
                    new_ids.add(mid)
                    all_movies[mid] = {
                        "id": mid,
                        "title": c.get("title", ""),
                        "release_date": c.get("release_date", ""),
                        "vote_average": c.get("vote_average", 0),
                        "vote_count": c.get("vote_count", 0),
                        "poster_path": c.get("poster_path"),
                        "genre_ids": c.get("genre_ids", []),
                        "_from_credits": True,
                    }
        print(f"    → новых фильмов: {len(new_ids)}")

        print("📥 [9b/10] Details новых фильмов...")
        sem_new = asyncio.Semaphore(15)
        async def enrich_new(mid):
            async with sem_new:
                d = await fetch_details(session, mid)
                if d: all_movies[mid]["_details"] = d
        await asyncio.gather(*[enrich_new(mid) for mid in new_ids])

        print("🖼️ [9c/10] Постеры новых фильмов...")
        sem_new_p = asyncio.Semaphore(12)
        async def dl_new(mid):
            async with sem_new_p:
                return mid, await download_poster(session, all_movies[mid].get("poster_path"), mid)
        new_poster_results = await asyncio.gather(*[dl_new(mid) for mid in new_ids])
        poster_ok.update(dict(new_poster_results))

    # ═══ 10. JSON ═══
    print("📦 [10/10] Формирование JSON...")
    top_actor_ids = {a["id"] for a in top_actors}
    movies_out = []
    for mid, m in all_movies.items():
        if not poster_ok.get(mid): continue
        d = m.get("_details", {})
        cast_details = []
        for c in d.get("cast_details", []):
            cd = dict(c)
            cd["profile_file"] = f"{c['id']}.webp" if c["id"] in top_actor_ids else ""
            cast_details.append(cd)
        movies_out.append({
            "id": mid,
            "title": m.get("title", ""),
            "original_title": m.get("original_title", ""),
            "year": (m.get("release_date", "") or "")[:4],
            "rating": round(float(m.get("vote_average", 0) or 0), 1),
            "vote_count": m.get("vote_count", 0),
            "overview": m.get("overview", ""),
            "genre_ids": m.get("genre_ids", []),
            "poster_file": f"{mid}.webp",
            "runtime": d.get("runtime", 0),
            "director": d.get("director", ""),
            "cast": d.get("cast", []),
            "cast_ids": d.get("cast_ids", []),
            "cast_details": cast_details,
            "crew": d.get("crew", []),
            "genres": d.get("genres", []),
            "production_companies": d.get("production_companies", []),
            "belongs_to_collection": d.get("belongs_to_collection"),
            "budget": d.get("budget", 0),
            "revenue": d.get("revenue", 0),
            "tagline": d.get("tagline", ""),
            "original_language": d.get("original_language", ""),
            "spoken_languages": d.get("spoken_languages", []),
            "keywords": d.get("keywords", []),
            "imdb_id": d.get("imdb_id", ""),
        })
    movies_out.sort(key=lambda x: -x["vote_count"])

    top_actors_clean = [{
        "id": a["id"],
        "name": a["name"],
        "profile_file": a["profile_file"],
        "movie_count": a["movie_count"],
        "avg_rating": a["avg_rating"],
        "votes_total": a["votes_total"],
        "is_legend": a["is_legend"],
        "is_popular": a["is_popular"],
    } for a in top_actors]

    data = {
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "genres": GENRES,
        "top_actors": top_actors_clean,
        "movies": movies_out,
    }
    with open(JSON_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    size_mb = os.path.getsize(JSON_FILE) / 1024 / 1024
    elapsed = (datetime.now() - t_start).total_seconds() / 60

    # ═══ Итоги ═══
    legends_in_top = sum(1 for a in top_actors if a["is_legend"])
    print(f"")
    print(f"════════ ГОТОВО за {elapsed:.1f} мин ════════")
    print(f"  Фильмов: {len(movies_out)}")
    print(f"  Актёров: {len(top_actors_clean)} (легенд: {legends_in_top}/150)")
    print(f"  JSON: {size_mb:.1f} MB")
    print(f"  Постеров: {sum(1 for v in poster_ok.values() if v)}")
    print(f"  Фото: {actor_ok_count}")

if __name__ == "__main__":
    asyncio.run(main())
