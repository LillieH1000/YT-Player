import json
import re
import yt_dlp

def getInfo(runtime, videoID):
    ytdlp_opts = {
        "cachedir": False,
        "check_formats": "selected",
        "extract_flat": True,
        "extractor_args": {
            "youtube": {
                "player_client": [
                    "default",
                    "tv_downgraded",
                    "visionos"
                ]
            }
        },
        "format": "bestvideo[protocol=m3u8_native]/best[protocol=m3u8_native]",
        "ignore_no_formats_error": True,
        "js_runtimes": {
            "deno": {
                "path": None
            },
            "quickjs": {
                "path": runtime
            }
        },
        "noplaylist": True,
        "playlist_items": "0"
    }

    info = {}
    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
        y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        z = ytdlp.extract_info(y["uploader_url"], download=False)

        a = ytdlp.urlopen(f"https://www.youtube.com/watch?v={y['id']}").read().decode("utf-8")
        b = re.search(r"ytInitialPlayerResponse\s*=\s*({.+?});", a)
        if b:
            c = json.loads(b.group(1))
            d = c["streamingData"]["adaptiveFormats"]
            e = { str(f["itag"]): f for f in d }
            if ("formats" in y):
                for f in y["formats"]:
                    itag = str(f["format_id"]).split("-")[0]
                    f["indexRange"] = e[itag]["indexRange"] if itag in e else None
                    f["initRange"] = e[itag]["initRange"] if itag in e else None

        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"] or y["uploader_id"]
        info["artwork"] = z["thumbnails"][-1]["url"]
        info["thumbnail"] = y["thumbnail"]
        info["description"] = y["description"]
        info["live"] = y["is_live"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["type"] = y["media_type"]
        info["duration"] = y.get("duration", None)
        
        availability = 0
        original = False
        
        video = []
        for g in y["formats"]:
            if "original" in g["format"]:
                original = True
            h = {}
            if (g["protocol"] == "https" and g["indexRange"] != None and g["container"] == "mp4_dash" and g["ext"] == "mp4"):
                h["codec"] = g["vcodec"]
                h["height"] = g["height"]
                h["width"] = g["width"]
                h["indexRange"] = g["indexRange"]
                h["initRange"] = g["initRange"]
                h["url"] = g["url"]
                if (g["available_at"] > availability):
                    availability = g["available_at"]
            if (len(h) != 0):
                video.append(h)
        info["video"] = video if (len(video) >= 1) else None

        audio = []
        for g in y["formats"]:
            h = {}
            if (g["protocol"] == "https" and (not original or "original" in g["format"]) and g["indexRange"] != None and g["container"] == "m4a_dash" and g["ext"] == "m4a"):
                h["codec"] = g["acodec"]
                h["indexRange"] = g["indexRange"]
                h["initRange"] = g["initRange"]
                h["url"] = g["url"]
                if (g["available_at"] > availability):
                    availability = g["available_at"]
            if (len(h) != 0):
                audio.append(h)
        info["audio"] = audio if (len(audio) >= 1) else None

        hls = {}
        if ("manifest_url" in y):
            hls["expiration"] = int(re.search("(?:/expire/|[?]expire=)(\\d+)", y["manifest_url"]).group(1))
            hls["url"] = y["manifest_url"]
            if ("available_at" in y and y["available_at"] > availability):
                availability = y["available_at"]
        info["hls"] = hls if (len(hls) >= 1) else None

        info["availability"] = availability

        subtitles = []
        for a in y["subtitles"]:
            c = {}
            for b in y["subtitles"][a]:
                if (b["ext"] == "vtt"):
                    c["id"] = a
                    c["name"] = b["name"]
                    c["url"] = b["url"]
            if (len(c) != 0):
                subtitles.append(c)
        info["subtitles"] = subtitles if (len(subtitles) >= 1) else None
        
    return json.dumps(info)