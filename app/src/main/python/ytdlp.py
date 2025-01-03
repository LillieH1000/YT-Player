import json
import yt_dlp

def getInfo(videoID):
    ytdlp_mweb = {
        "extractor_args": {
            "youtube": {
                "player_client": ["mweb"]
            }
        },
        "format": "bestvideo[ext=mp4]+bestaudio[ext=m4a]",
        "noplaylist": True
    }

    ytdlp_ios = {
        "extractor_args": {
            "youtube": {
                "player_client": ["ios"]
            }
        },
        "hls_prefer_native": True,
        "noplaylist": True
    }

    info = {}

    with yt_dlp.YoutubeDL(ytdlp_ios) as ytdlp:
        x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
        y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
        info["id"] = y["id"]
        info["title"] = y["title"]
        info["author"] = y["uploader"]
        info["artwork"] = y["thumbnail"]
        info["views"] = y["view_count"]
        info["likes"] = y["like_count"]
        info["live"] = y["is_live"]
        info["hlsUrl"] = y["requested_formats"][0]["manifest_url"]

    a = json.loads(json.dumps(info))

    if not a["live"]:
        with yt_dlp.YoutubeDL(ytdlp_mweb) as ytdlp:
            x = ytdlp.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
            y = json.loads(json.dumps(ytdlp.sanitize_info(x)))
            info["videoUrl"] = y["requested_formats"][0]["url"]
            info["audioUrl"] = y["requested_formats"][1]["url"]

    if a["live"]:
        info["videoUrl"] = None
        info["audioUrl"] = None
        
    return json.dumps(info)