import yt_dlp

def getHls(videoID):
    ytdlp_opts = {
    }

    with yt_dlp.YoutubeDL(ytdlp_opts) as ydl:
        info = ydl.extract_info(f"https://www.youtube.com/watch?v={videoID}", download=False)
        return info