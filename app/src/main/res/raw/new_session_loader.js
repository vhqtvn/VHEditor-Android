export async function main(...args) {
    const default_main = (await gmodule.import("./new-session-default")).main
    return default_main(...args)
}
