import os
import re
import shutil
import subprocess


def highlight(match):
    group = match.group(1)
    proc = subprocess.Popen(
        ["java", "-jar", "../app/build/libs/app-all.jar", "--syntax-highlight", "-c", group],
        stdout=subprocess.PIPE
    )
    return proc.communicate()[0].decode("utf-8")


metis_code = re.compile(r"<metis>(.*?)</metis>", re.DOTALL)
dest = "../gendocs"

if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.dirname(__file__)))
    os.system("./gradlew shadowJar")
    os.system("./gradlew dokkaHtml")
    os.chdir(os.path.dirname(__file__))
    if not os.path.exists(dest):
        os.mkdir(dest)

    for dirpath, _, files in os.walk("./"):
        for file in files:
            if not file.endswith(".papyri") or file.endswith(".lib.papyri"):
                continue

            source = os.path.abspath(f"{dirpath}/{file}")

            with open(source, "r") as f:
                content = f.read()

            print(f"Processing {source}")
            proc = subprocess.Popen(
                ["papyri", "-i"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                stdin=subprocess.PIPE
            )
            content, stderr = proc.communicate(input=content.encode("utf-8"))
            if stderr:
                print(stderr.decode("utf-8"))
                exit(1)
            content = metis_code.sub(highlight, content.decode("utf-8"))
            file = file.replace(".papyri", ".html")
            path = f"{dest}/{dirpath}"
            if not os.path.exists(path):
                os.makedirs(path)
            with open(f"{path}/{file}", "w") as f:
                f.write(content)

    if os.path.exists(f"{dest}/static"):
        shutil.rmtree(f"{dest}/static")
    shutil.copytree("static", f"{dest}/static")
