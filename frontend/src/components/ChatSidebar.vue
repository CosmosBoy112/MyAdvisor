<script setup>
import {MessageCircle,Plus} from 'lucide-vue-next';
defineProps({items:{type:Array,default:()=>[]},activeId:{type:String,default:null},disabled:Boolean,t:{type:Function,required:true}});
const emit=defineEmits(['select','add']);
const day=value=>value?new Date(value).toLocaleDateString(undefined,{month:'short',day:'numeric'}):'';
</script>
<template>
 <nav class="chat-sidebar" :aria-label="t('Your chats')">
  <button type="button" class="chat-sidebar-new" :disabled="disabled" @click="emit('add')"><Plus :size="18"/> {{t("New report")}}</button>
  <p class="chat-sidebar-label">{{t("Your chats")}}</p>
  <ul>
   <li v-for="item in items" :key="item.id">
    <button type="button" class="chat-sidebar-item" :class="{active:item.id===activeId}" :aria-current="item.id===activeId?'true':undefined" :disabled="disabled&&item.id!==activeId" @click="item.id!==activeId&&emit('select',item.id)">
     <MessageCircle :size="16"/>
     <span><strong>{{item.lastQuestion||t('New conversation')}}</strong><small>{{item.major||item.filename}} · {{day(item.createdAt)}}</small></span>
    </button>
   </li>
  </ul>
 </nav>
</template>
<style scoped>
.chat-sidebar{flex:0 0 260px;align-self:flex-start;margin-top:8px;height:calc(100dvh - 170px);min-height:480px;display:flex;flex-direction:column;gap:6px;padding:16px 12px;background:var(--surface);border-radius:28px;box-shadow:var(--card-shadow);overflow-y:auto;z-index:20}
.chat-sidebar-new{display:flex;align-items:center;justify-content:center;gap:8px;min-height:44px;border-radius:999px;background:var(--psu-navy);color:#fff;font-size:var(--text-small);font-weight:550}
.chat-sidebar-new:hover{background:#1e407c;color:#fff}
.chat-sidebar-label{margin:14px 8px 4px;font-size:12px;font-weight:600;letter-spacing:.06em;text-transform:uppercase;color:var(--muted)}
ul{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:4px}
.chat-sidebar-item{width:100%;display:flex;align-items:flex-start;gap:10px;padding:10px 12px;border-radius:16px;text-align:left;color:var(--ink)}
.chat-sidebar-item svg{flex-shrink:0;margin-top:3px;color:var(--muted)}
.chat-sidebar-item span{min-width:0;flex:1}
.chat-sidebar-item strong{display:block;font-size:var(--text-small);font-weight:500;line-height:1.45;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chat-sidebar-item small{display:block;margin-top:2px;font-size:12px;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chat-sidebar-item:hover:not(:disabled){background:var(--surface-subtle)}
.chat-sidebar-item.active{background:var(--accent-soft)}
.chat-sidebar-item.active strong,.chat-sidebar-item.active svg{color:var(--psu-blue)}
.chat-sidebar-item:disabled{cursor:not-allowed;opacity:.55}
:global([data-theme="dark"]) .chat-sidebar-new{background:#1e407c}
@media(max-width:1100px){.chat-sidebar{display:none}}
</style>
