import {francAll} from 'franc-min';
export const supportedLanguages=['en','ko','zh','ja','es','fr','de','pt','ru','hi'];
const latin={eng:'en',spa:'es',fra:'fr',deu:'de',por:'pt'};
const words={
 en:'the how what which my remaining credits courses please explain need can hello thanks',
 es:'hola gracias cuántos cuántas qué necesito quedan asignaturas puedo carrera créditos',
 fr:'bonjour merci combien quels quelles dois cours reste crédits diplôme peux',
 de:'hallo danke wie welche muss noch brauche leistungspunkte studium erklären',
 pt:'olá obrigado obrigada quantos quantas preciso faltam disciplinas posso graduação créditos'
};
export function detectInputLanguage(input,current='en'){
 const fallback=supportedLanguages.includes(current)?current:'en';
 const text=String(input??'').normalize('NFC').replace(/\b[A-Z]{2,8}\s*\d{1,4}[A-Z]?\b/g,' ').trim();
 if(/[가-힣ㄱ-ㅎㅏ-ㅣ]/u.test(text))return 'ko';
 if(/[\u3040-\u30ff]/u.test(text))return 'ja';
 if(/[\u4e00-\u9fff]/u.test(text))return 'zh';
 if(/[\u0900-\u097f]/u.test(text))return 'hi';
 if(/[\u0400-\u04ff]/u.test(text))return 'ru';
 const tokens=text.toLowerCase().match(/\p{L}+/gu)||[];
 const scores=Object.entries(words).map(([code,vocab])=>[code,tokens.filter(w=>vocab.split(' ').includes(w)).length]).sort((a,b)=>b[1]-a[1]);
 if(scores[0][1]>0&&scores[0][1]>scores[1][1])return scores[0][0];
 if(tokens.length<3||text.length<18)return fallback;
 const candidates=francAll(text,{only:Object.keys(latin),minLength:18});
 // A distance gap is a heuristic, not a calibrated probability. Preserve the
 // previous language for ambiguous snippets instead of flickering while typing.
 if(candidates[0]?.[0]!=='und'&&candidates[0]?.[1]-candidates[1]?.[1]>0.08)return latin[candidates[0][0]]??fallback;
 return fallback;
}
