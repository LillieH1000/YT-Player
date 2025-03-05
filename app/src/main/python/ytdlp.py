import json
import yt_dlp

def getInfo(videoID):
    ytdlp_opts = {
        "extractor_args": {
            "youtube": {
                "player_client": ["ios"]
            }
        },
        "format": "bestvideo[protocol=m3u8_native]",
        "noplaylist": True
    }

    info = {}

    with yt_dlp.YoutubeDL(ytdlp_opts) as ytdlp:
        x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
        y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"]
        info["artwork"] = y["thumbnail"]
        info["live"] = y["is_live"]
        info["url"] = y["manifest_url"]
        if ("en" in y["subtitles"]):
            for i in y["subtitles"]["en"]:
                if (i["ext"] == "vtt"):
                    info["enCaptions"] = i["url"]
        if ("enCaptions" not in info):
            info["enCaptions"] = None
        
    return json.dumps(info)