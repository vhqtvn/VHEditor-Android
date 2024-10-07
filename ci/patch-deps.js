const fs = require("fs");
const cp = require('child_process');
const exec = cp.execSync;

hack("deps/termux-app/termux-shared/build.gradle", (s) => {
    return s.replace(
        /implementation "com\.google\.guava:guava:[^"]*"/sg,
        'implementation "com.google.guava:guava:31.0.1-jre"'
    )
})

for(const f of [
    "deps/termux-app/terminal-view/build.gradle",
    "deps/termux-app/terminal-emulator/build.gradle",
    "deps/termux-app/termux-shared/build.gradle",
])
hack(f, (s) => {
    // https://youtrack.jetbrains.com/issue/KT-55624
    s = s.replace(
        /classifier "sources"/sg,
        'archiveClassifier.set("sources")'
    )

    s = s.replace(
        /release\(MavenPublication\) \{.*?\}/sg,
        ''
    )

    return s
})

hack("deps/termux-app/termux-shared/src/main/java/com/termux/shared/interact/MessageDialogUtils.java", (s) => {
    return s.replace(
        ", R.style.Theme_AppCompat_Light_Dialog",
        ', androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog'
    )
});

function hack(file, transform) {
    if (Array.isArray(file)) {
        for (const f of file) hack(f, transform)
        return;
    }
    let content = fs.readFileSync(file, { encoding: 'utf8' })
    let content2 = transform(content)
    if (content2 !== content) {
        console.log("hack " + file);
        fs.writeFileSync(file, content2);
    }
}