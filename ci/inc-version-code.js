const fs = require("fs")
const path = require("path")

const fn = path.join(__dirname, "..", "package.json")

let package_json = JSON.parse(fs.readFileSync(fn))

package_json.config.versionCode++

fs.writeFileSync(fn, JSON.stringify(package_json, null, 2))

console.log(`Bump versionCode to ${package_json.config.versionCode}`)