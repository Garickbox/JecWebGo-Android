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

# Discover для новых популярных (2023+)
YEARS = [2023, 2024, 2025, 2026]

# Актёры
ACTOR_MIN_YEAR = 1980          # фильтр фильмографий
ACTOR_FILMS_LIMIT = 30         # фильмов на актёра
ACTOR_MIN_MOVIES = 3           # минимум фильмов в базе для кандидата
TOP_ACTORS_LIMIT = 500         # всего актёров с фото
POPULAR_PEOPLE_PAGES = 20      # 1000 популярных для верификации

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
            # ВСЕ id актёров (не только top-15) — для byActorId
            cast_ids = [c.get("id", 0) for c in credits.get("cast", []) if c.get("id", 0) > 0]
            # Топ-15 с деталями (фото, character)
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

async def fetch_person_credits(session, person_id):
    """Полная фильмография актёра: cast-only, 1980+, не voice, sorted by rating."""
    url = f"{TMDB_BASE}/person/{person_id}/movie_credits?language=ru-RU&api_key={TMDB_API_KEY}"
    try:
        async with session.get(url, timeout=20) as r:
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
        print(f"  PIL fail {movie_id}: {e}"); return False

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
        print(f"  actor PIL fail {person_id}: {e}"); return False

async def fetch_popular_people(session):
    """Только для верификации «это реально известный человек» — не для фильтра."""
    out = set()
    for page in range(1, POPULAR_PEOPLE_PAGES + 1):
        url = f"{TMDB_BASE}/person/popular?language=ru-RU&page={page}&api_key={TMDB_API_KEY}"
        try:
            async with session.get(url, timeout=15) as r:
                if r.status != 200: continue
                data = await r.json()
                for p in data.get("results", []):
                    out.add(p.get("id", 0))
        except Exception as e:
            print(f"  popular err p{page}: {e}")
    return out

async def main():
    if not TMDB_API_KEY:
        print("ERROR: TMDB_API_KEY not set"); sys.exit(1)
    os.makedirs(POSTERS_DIR, exist_ok=True)
    os.makedirs(ACTORS_DIR, exist_ok=True)
    all_movies = {}

    async with aiohttp.ClientSession() as session:
        # ═══ 1. Discover (18 жанров × 4 года) ═══
        print("🔍 Discover по 18 жанрам × 4 года × 3 страницы...")
        for g in GENRES:
            results = await fetch_discover(session, g["id"])
            print(f"  {g['name']}: {len(results)}")
            for m in results:
                if m["id"] not in all_movies:
                    all_movies[m["id"]] = m
        print(f"📊 Origin фильмов: {len(all_movies)}")

        # ═══ 2. Details для origin ═══
        print("📥 Детали origin...")
        sem = asyncio.Semaphore(15)
        async def enrich(mid):
            async with sem:
                d = await fetch_details(session, mid)
                if d: all_movies[mid]["_details"] = d
        await asyncio.gather(*[enrich(mid) for mid in list(all_movies.keys())])

        # ═══ 3. Постеры origin ═══
        print("🖼️ Постеры origin...")
        sem2 = asyncio.Semaphore(12)
        async def dl(mid):
            async with sem2:
                return mid, await download_poster(session, all_movies[mid].get("poster_path"), mid)
        results = await asyncio.gather(*[dl(mid) for mid in list(all_movies.keys())])
        poster_ok = dict(results)
        ok_count = sum(1 for v in poster_ok.values() if v)
        print(f"✅ Постеров origin: {ok_count}/{len(all_movies)}")

        # ═══ 4. Агрегация актёров из origin ═══
        print("🎭 Агрегация актёров из origin...")
        actor_stats = {}
        for mid, m in all_movies.items():
            d = m.get("_details", {})
            if not d: continue
            for c in d.get("cast_details", []):
                aid = c.get("id", 0)
                if aid == 0: continue
                if aid not in actor_stats:
                    actor_stats[aid] = {
                        "name": c["name"],
                        "profile_path": c["profile_path"],
                        "movie_count": 0,
                        "ratings": [],
                        "votes": [],
                    }
                actor_stats[aid]["movie_count"] += 1
                r = float(m.get("vote_average", 0) or 0)
                if r > 0: actor_stats[aid]["ratings"].append(r)
                v = int(m.get("vote_count", 0) or 0)
                if v > 0: actor_stats[aid]["votes"].append(v)

        # ═══ 5. Верификация через /person/popular (нужна для «известности») ═══
        print(f"🎭 Верификация через popular ({POPULAR_PEOPLE_PAGES} страниц)...")
        popular_ids = await fetch_popular_people(session)
        print(f"  Популярных: {len(popular_ids)}")

        # ═══ 6. Кандидаты: movie_count >= ACTOR_MIN_MOVIES, есть фото ═══
        candidates = []
        for aid, s in actor_stats.items():
            if s["movie_count"] < ACTOR_MIN_MOVIES: continue
            if not s["profile_path"]: continue  # обязательно фото
            avg_r = sum(s["ratings"]) / len(s["ratings"]) if s["ratings"] else 0
            votes_total = sum(s["votes"])
            in_popular = 1 if aid in popular_ids else 0
            candidates.append({
                "id": aid,
                "name": s["name"],
                "profile_path": s["profile_path"],
                "movie_count": s["movie_count"],
                "avg_rating": round(avg_r, 1),
                "votes_total": votes_total,
                "in_popular": in_popular,
            })
        print(f"  Кандидатов (movie_count>={ACTOR_MIN_MOVIES}, есть фото): {len(candidates)}")

        # ═══ 7. Score: БЕЗ popularity, С votes_total + popular boost ═══
        max_count = max((c["movie_count"] for c in candidates), default=1)
        for c in candidates:
            c["score"] = round(
                (c["movie_count"] / max_count) * 100 * 0.4
                + c["avg_rating"] * 0.3
                + min(c["votes_total"] / 10000.0, 50) * 0.3
                + c["in_popular"] * 15,   # boost для «сегодняшних» звёзд
                2
            )
        top_actors = sorted(candidates, key=lambda x: -x["score"])[:TOP_ACTORS_LIMIT]
        print(f"🎭 Топ-{TOP_ACTORS_LIMIT} актёров отобрано (из {len(candidates)})")

        # ═══ 8. Фото 500 актёров ═══
        print(f"📸 Фото {len(top_actors)} актёров (w185)...")
        sem3 = asyncio.Semaphore(10)
        async def dl_actor(a):
            async with sem3:
                return a["id"], await download_actor_photo(session, a["profile_path"], a["id"])
        actor_results = await asyncio.gather(*[dl_actor(a) for a in top_actors])
        actor_ok = dict(actor_results)
        for a in top_actors:
            a["profile_file"] = f"{a['id']}.webp" if actor_ok.get(a["id"]) else ""
        # Убираем актёров без фото
        top_actors = [a for a in top_actors if a["profile_file"]]
        actor_ok_count = len(top_actors)
        print(f"✅ Фото актёров: {actor_ok_count}")

        # ═══ 9. Фильмографии 500 актёров ═══
        print(f"🎬 Фильмографии {len(top_actors)} актёров (cast-only, 1980+, не voice)...")
        credits_by_actor = {}
        sem_credits = asyncio.Semaphore(15)
        async def fetch_cred(a):
            async with sem_credits:
                credits_by_actor[a["id"]] = await fetch_person_credits(session, a["id"])
        await asyncio.gather(*[fetch_cred(a) for a in top_actors])

        # ═══ 10. Новые фильмы (нет в origin) ═══
        new_ids = set()
        for aid, credits in credits_by_actor.items():
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
        print(f"🆕 Новых фильмов из фильмографий: {len(new_ids)}")

        # ═══ 11. Details для новых ═══
        print("📥 Детали новых фильмов...")
        sem_new = asyncio.Semaphore(15)
        async def enrich_new(mid):
            async with sem_new:
                d = await fetch_details(session, mid)
                if d: all_movies[mid]["_details"] = d
        await asyncio.gather(*[enrich_new(mid) for mid in new_ids])

        # ═══ 12. Постеры новых ═══
        print("🖼️ Постеры новых...")
        sem_new_p = asyncio.Semaphore(12)
        async def dl_new(mid):
            async with sem_new_p:
                return mid, await download_poster(session, all_movies[mid].get("poster_path"), mid)
        new_poster_results = await asyncio.gather(*[dl_new(mid) for mid in new_ids])
        poster_ok.update(dict(new_poster_results))

    # ═══ Формирование JSON ═══
    print("📦 Формирование JSON...")
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
    print(f"✅ ФИНАЛ: {len(movies_out)} фильмов, {len(top_actors_clean)} актёров, JSON {size_mb:.1f} MB")
    print(f"✅ Постеров: {sum(1 for v in poster_ok.values() if v)}")
    print(f"✅ Фото актёров: {actor_ok_count}")

if __name__ == "__main__":
    asyncio.run(main())
