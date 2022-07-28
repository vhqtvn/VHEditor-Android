const fs = require("fs");
const cp = require('child_process');
const exec = cp.execSync;

hack("deps/termux-app/termux-shared/build.gradle", (s) => {
    return s.replace(
        /implementation "com\.google\.guava:guava:[^"]*"/sg,
        'implementation "com.google.guava:guava:31.0.1-jre"'
    )
})

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