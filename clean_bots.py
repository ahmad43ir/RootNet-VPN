import re

def clean_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Remove MENU_BTN_WEB constant
    content = re.sub(r"export const MENU_BTN_WEB = '🔗 Web pages';\n", '', content)
    
    # Remove WEB_LINKS array
    content = re.sub(r'const WEB_LINKS: \[string, string\]\[\] = \[[\s\S]*?\n\];\n?', '', content)
    
    # Fix keyboard array
    content = content.replace('[MENU_BTN_FILES, MENU_BTN_WEB, MENU_BTN_SCRAPER, MENU_BTN_VERSION],',
                              '[MENU_BTN_FILES, MENU_BTN_SCRAPER, MENU_BTN_VERSION],')
    
    # Remove HELP_TEXT Web pages lines
    content = content.replace("'🔗 Web pages — quick links\\n' +", '')
    content = content.replace("'🔗 *Web pages*\\n' +", '')
    
    # Remove the handler block: if (text === MENU_BTN_WEB) { ... }
    pattern = r'if \(text === MENU_BTN_WEB\) \{[\s\S]*?\n\s*\}\s*\n'
    content = re.sub(pattern, '', content, count=1, flags=re.DOTALL)
    
    # Remove any remaining MENU_BTN_WEB references
    content = content.replace("MENU_BTN_WEB", "// REMOVED MENU_BTN_WEB")
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

for p in ["rootnet-vpn/supabase/functions/telegram-bot/_handlers.ts",
          "rootnet-vpn/supabase/functions/vlesshub-bot/_handlers.ts"]:
    clean_file(p)

print("Done")