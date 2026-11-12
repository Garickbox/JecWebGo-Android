#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import asyncio, aiohttp, json, os, sys
from io import BytesIO
from datetime import datetime, timezone
from PIL import Image

TMDB_API_KEY = os.getenv("TMDB_API_KEY", "")
TMDB_BASE = "https://api.themoviedb.org/3"
MOVIES_PER_GENRE = 50
POSTERS_DIR = "posters_tmdb"
JSON_FILE = "tmdb_movies.json"
YEARS = [2024, 2025, 2026]

GENRES = [
    {"id": 28, "name": "Боевик", "emoji": "💥"},
    {"id": 35, "name": "Комедия", "emoji": "😂"},
    {"id": 878, "name": "Фантастика", "emoji": "🚀"},
    {"id": 27, "name": "Ужасы", "emoji": "👻"},
    {"id": 18, "name": "Драма", "emoji": "🎭"},
    {"id": 9648, "name": "Детектив", "emoji": "🕵️"},
    {"id": 10749, "name": "Мелодрама", "emoji": "💕"},
    {"id": 12, "name": "Приключения", "emoji": "🗺️"},
    {"id": 14, "name": "Фэнтези", "emoji": "🏰"},
    {"id": 10752, "name": "Военный", "emoji": "⚔️"},
    {"id": 16, "name": "Мультфильм", "emoji": "🎨"},
    {"id": 36, "name": "Исторический", "emoji": "📜"},
]

async def fetch_discover(session, genre_id):
    out = []
    for year in YEARS:
        for page in (1, 2):
            url = (f"{TMDB_BASE}/discover/movie?with_genres={genre_id}"
                   f"&sort_by=popularity.desc&primary_release_year={year}"
                   f"&vote_count.gte=20&language=ru-RU&page={page}&api_key={TMDB_API_KEY}")
            try:
                async with session.get(url, timeout=15) as r:
                    if r.status != 200: continue
                    data = await r.json()
                    for m in data.get("results", []):
                        out.append(m)
                        if len(out) >= MOVIES_PER_GENRE * 2: return out[:MOVIES_PER_GENRE]
            except: pass
        if len(out) >= MOVIES_PER_GENRE * 2: break
    return out[:MOVIES_PER_GENRE]

async def fetch_details(session, movie_id):
    url = (f"{TMDB_BASE}/movie/{movie_id}?language=ru-RU"
           f"&append_to_response=credits&api_key={TMDB_API_KEY}")
    try:
        async with session.get(url, timeout=15) as r:
            if r.status != 200: return None
            data = await r.json()
            credits = data.get("credits", {})
            director = ""
            for c in credits.get("crew", []):
                if c.get("job") == "Director":
                    director = c.get("name", ""); break
            cast = [c.get("name", "") for c in credits.get("cast", [])[:5]]
            return {
                "runtime": data.get("runtime", 0) or 0,
                "director": director,
                "cast": cast,
                "genres": [g["name"] for g in data.get("genres", [])],
            }
    except: return None

async def download_poster(session, poster_path, movie_id):
    if not poster_path: return False
    url = f"https://image.tmdb.org/t/p/w500{poster_path}"
    try:
        async with session.get(url, timeout=20) as r:
            if r.status != 200: return False
            content = await r.read()
        img = Image.open(BytesIO(content)).convert("RGB")
        img.thumbnail((400, 600), Image.LANCZOS)
        img.save(f"{POSTERS_DIR}/{movie_id}.webp", "WEBP", quality=78, method=6)
        return True
    except Exception as e:
        print(f"  PIL fail {movie_id}: {e}"); return False

async def main():
    if not TMDB_API_KEY:
        print("ERROR: TMDB_API_KEY not set"); sys.exit(1)
    os.makedirs(POSTERS_DIR, exist_ok=True)
    all_movies = {}

    async with aiohttp.ClientSession() as session:
        print("🔍 Discover по жанрам...")
        for g in GENRES:
            results = await fetch_discover(session, g["id"])
            print(f"  {g['name']}: {len(results)}")
            for m in results:
                if m["id"] not in all_movies:
                    all_movies[m["id"]] = m
        print(f"📊 Уникальных: {len(all_movies)}")

        print("📥 Детали...")
        sem = asyncio.Semaphore(20)
        async def enrich(mid):
            async with sem:
                d = await fetch_details(session, mid)
                if d: all_movies[mid]["_details"] = d
        await asyncio.gather(*[enrich(mid) for mid in list(all_movies.keys())])

        print("🖼️ Постеры...")
        sem2 = asyncio.Semaphore(15)
        async def dl(mid):
            async with sem2:
                pp = all_movies[mid].get("poster_path")
                return mid, await download_poster(session, pp, mid)
        results = await asyncio.gather(*[dl(mid) for mid in list(all_movies.keys())])
        poster_ok = dict(results)
        ok_count = sum(1 for v in poster_ok.values() if v)
        print(f"✅ Постеров: {ok_count}/{len(all_movies)}")

    movies_out = []
    for mid, m in all_movies.items():
        if not poster_ok.get(mid): continue
        d = m.get("_details", {})
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
            "genres": d.get("genres", []),
        })
    movies_out.sort(key=lambda x: -x["vote_count"])

    data = {
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "genres": GENRES,
        "movies": movies_out,
    }
    with open(JSON_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print(f"✅ {len(movies_out)} фильмов → {JSON_FILE}")
    print(f"✅ {ok_count} постеров → {POSTERS_DIR}/")

if __name__ == "__main__":
    asyncio.run(main())
