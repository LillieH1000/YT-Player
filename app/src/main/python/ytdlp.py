import json
import re
import yt_dlp

def getInfo(runtime, videoID, searchQuery):
    ytdlp_opts = {
        "cachedir": False,
        "check_formats": "selected",
        "extractor_args": {
            "youtube": {
                "player_client": [
                    "default",
                    "-ios",
                    "web_safari"
                ]
            }
        },
        "format": "best[protocol=m3u8_native]",
        "js_runtimes": {
            "deno": {
                "path": None
            },
            "quickjs": {
                "path": runtime
            }
        },
        "noplaylist": True,
        "remote_components": [
            "ejs:github"
        ]
    }

    info = {}
    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        if (searchQuery == None):
            x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
            y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        if (videoID == None):
            x = ytdlp.extract_info(f"ytsearch:{searchQuery}", download=False)
            z = json.loads(json.dumps(ytdlp.sanitize_info(x)))
            y = z["entries"][0]

        a = ytdlp.urlopen(f"https://www.youtube.com/watch?v={y['id']}").read().decode("utf-8")
        b = re.search(r"ytInitialPlayerResponse\s*=\s*({.+?});", a)
        if b:
            c = json.loads(b.group(1))
            d = c["streamingData"]["adaptiveFormats"]
            e = {str(f["itag"]): f for f in d}
            if ("formats" in y):
                for f in y["formats"]:
                    itag = str(f["format_id"]).split("-")[0]
                    f["indexRange"] = e[itag]["indexRange"] if itag in e else None
                    f["initRange"] = e[itag]["initRange"] if itag in e else None

        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"]
        info["artwork"] = y["thumbnail"]
        info["live"] = y["is_live"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["type"] = y["media_type"]
        info["expiration"] = re.search("(?:/expire/|[?]expire=)(\\d+)", y["requested_formats"][0]["url"] if "requested_formats" in y else y["url"]).group(1)
        info["duration"] = y.get("duration", None)
        info["hlsUrl"] = y["url"] if not ("requested_formats" in y) else None

        video = []
        for g in y["formats"]:
            h = {}
            if (g["protocol"] == "https" and g["indexRange"] != None and g["ext"] == "mp4"):
                h["codec"] = g["vcodec"]
                h["height"] = g["height"]
                h["width"] = g["width"]
                h["indexRange"] = g["indexRange"]
                h["initRange"] = g["initRange"]
                h["url"] = g["url"]
            if (len(h) != 0):
                video.append(h)
        info["video"] = video if (len(video) >= 1) else None

        audio = []
        for g in y["formats"]:
            h = {}
            if (g["protocol"] == "https" and g["indexRange"] != None and g["ext"] == "m4a"):
                h["codec"] = g["acodec"]
                h["indexRange"] = g["indexRange"]
                h["initRange"] = g["initRange"]
                h["url"] = g["url"]
            if (len(h) != 0):
                audio.append(h)
        info["audio"] = audio if (len(audio) >= 1) else None

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