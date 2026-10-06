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
                    "visionos"
                ]
            }
        },
        "format": "bestvideo[protocol=m3u8_native]/best[protocol=m3u8_native]",
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
        z = ytdlp.extract_info(y["channel_url"], download=False)

        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["channel"] or y["channel_id"]
        info["artwork"] = z["thumbnails"][-1]["url"]
        info["channel"] = y["channel_url"]
        info["thumbnail"] = y["thumbnail"]
        info["description"] = y["description"] or None
        info["live"] = y["is_live"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["type"] = y["media_type"]
        
        hls = {}
        availability = 0
        hls["expiration"] = int(re.search("(?:/expire/|[?]expire=)(\\d+)", y["manifest_url"]).group(1))
        hls["url"] = y["manifest_url"]
        if ("available_at" in y):
            availability = y["available_at"]

        info["hls"] = hls
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