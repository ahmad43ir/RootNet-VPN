// ============================================================
// 📁 _state.ts — PER-CHAT STATE PERSISTENCE (bot_chat_state)
// ============================================================
// The Python bot kept SELECTED / UPLOAD_MODE / LIST_MODE in module
// memory. Edge functions are stateless, so this UI state lives in
// Postgres (see migration 20260807000002). service_role only.
// ============================================================

export interface ChatState {
  chatId: number;
  uploadMode: boolean;
  listMode: boolean;
  selectedIds: number[];
  /** Set when the bot is waiting for a value after the user tapped a
   *  menu button. */
  pendingInput: string | null;
  /** Message ID of the current live menu/option message (deleted when a
   *  new menu page is shown, so keyboards don't pile up). */
  menuMessageId: number | null;
  /** Message ID of the current live result/status message (deleted when a
   *  new result replaces it, so upload summaries don't pile up). */
  resultMessageId: number | null;
}

const SECRET_KEY = 'telegram_webhook_secret';

export async function getChatState(supabase: any, chatId: number): Promise<ChatState> {
  try {
    const { data } = await supabase
      .from('bot_chat_state')
      .select('upload_mode, list_mode, selected_ids, pending_input, menu_message_id, result_message_id')
      .eq('chat_id', chatId)
      .maybeSingle();
    if (data) {
      return {
        chatId,
        uploadMode: data.upload_mode === true,
        listMode: data.list_mode === true,
        selectedIds: Array.isArray(data.selected_ids)
          ? data.selected_ids.map(Number)
          : [],
        pendingInput: typeof data.pending_input === 'string' && data.pending_input ? data.pending_input : null,
        menuMessageId: typeof data.menu_message_id === 'number' ? data.menu_message_id : null,
        resultMessageId: typeof data.result_message_id === 'number' ? data.result_message_id : null,
      };
    }
  } catch (e) {
    console.warn('[state] getChatState failed:', (e as Error).message);
  }
  return { chatId, uploadMode: false, listMode: false, selectedIds: [], pendingInput: null, menuMessageId: null, resultMessageId: null };
}

export async function saveChatState(supabase: any, state: ChatState): Promise<void> {
  try {
    await supabase.from('bot_chat_state').upsert(
      {
        chat_id: state.chatId,
        upload_mode: state.uploadMode,
        list_mode: state.listMode,
        selected_ids: state.selectedIds,
        pending_input: state.pendingInput,
        menu_message_id: state.menuMessageId,
        result_message_id: state.resultMessageId,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'chat_id' },
    );
  } catch (e) {
    console.warn('[state] saveChatState failed:', (e as Error).message);
  }
}

export async function getWebhookSecret(supabase: any): Promise<string | null> {
  try {
    const { data } = await supabase
      .from('bot_config')
      .select('value')
      .eq('key', SECRET_KEY)
      .maybeSingle();
    return data?.value ?? null;
  } catch (e) {
    console.warn('[state] getWebhookSecret failed:', (e as Error).message);
    return null;
  }
}

export async function saveWebhookSecret(supabase: any, secret: string): Promise<void> {
  try {
    await supabase.from('bot_config').upsert(
      {
        key: SECRET_KEY,
        value: secret,
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'key' },
    );
  } catch (e) {
    console.warn('[state] saveWebhookSecret failed:', (e as Error).message);
  }
}
