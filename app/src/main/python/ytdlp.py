import json
import re
import yt_dlp

def getInfo(videoID, searchQuery):
    ytdlp_opts = {
        "extractor_args": {
            "youtube": {
                "player_client": ["ios","web_safari"]
            }
        },
        "format": "bestvideo[protocol=m3u8_native]/best[protocol=m3u8_native]",
        "check_formats": "selected",
        "noplaylist": True,
        "cachedir": False
    }

    info = {}
    subtitles = []

    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        if (searchQuery == None):
            x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
            y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        if (videoID == None):
            x = ytdlp.extract_info(f"ytsearch:{searchQuery}", download=False)
            z = json.loads(json.dumps(ytdlp.sanitize_info(x)))
            y = z["entries"][0]

        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"]
        info["artwork"] = y["thumbnail"]
        info["live"] = y["is_live"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["type"] = y["media_type"]
        info["url"] = y["manifest_url"]
        info["expiration"] = re.search("/expire/(\\d+)/", y["manifest_url"]).group(1)
        for a in y["subtitles"]:
            c = {}
            for b in y["subtitles"][a]:
                if (b["ext"] == "vtt"):
                    c["id"] = a
                    c["name"] = b["name"]
                    c["url"] = b["url"]
            if (len(c) != 0):
                subtitles.append(c)
        if (len(subtitles) == 0):
            info["subtitles"] = None
        else:
            info["subtitles"] = subtitles
        
    return json.dumps(info)