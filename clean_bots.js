const fs = require('fs');

function cleanFile(path) {
    let content = fs.readFileSync(path, 'utf8');

    // Remove MENU_BTN_WEB constant
    content = content.replace(/export const MENU_BTN_WEB = '🔗 Web pages';\n/, '');

    // Remove WEB_LINKS array
    content = content.replace(/const WEB_LINKS: \[string, string\]\[\] = \[[\s\S]*?\n\];\n?/, '');

    // Fix keyboard array
    content = content.replace(
        '[MENU_BTN_FILES, MENU_BTN_WEB, MENU_BTN_SCRAPER, MENU_BTN_VERSION],',
        '[MENU_BTN_FILES, MENU_BTN_SCRAPER, MENU_BTN_VERSION],'
    );

    // Remove HELP_TEXT Web pages lines
    content = content.replace("'🔗 Web pages — quick links\\n' +", '');
    content = content.replace("'🔗 *Web pages*\\n' +", '');

    // Remove the handler block: if (text === MENU_BTN_WEB) { ... }
    content = content.replace(/if \(text === MENU_BTN_WEB\) \{[\s\S]*?\n\s*\}\s*\n/, '');

    // Remove any remaining MENU_BTN_WEB references
    content = content.replace(/MENU_BTN_WEB/g, '// REMOVED MENU_BTN_WEB');

    fs.writeFileSync(path, content, 'utf8');
}

['rootnet-vpn/supabase/functions/telegram-bot/_handlers.ts',
 'rootnet-vpn/supabase/functions/vlesshub-bot/_handlers.ts'].forEach(cleanFile);

console.log('Done');