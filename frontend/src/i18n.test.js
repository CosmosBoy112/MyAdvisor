import test from 'node:test';
import assert from 'node:assert/strict';
import {locale,t,detectInputLanguage,useInputLanguage} from './i18n.js';
test('UI starts in English and follows explicit input rather than the browser language',()=>{
 assert.equal(locale.value,'en');assert.equal(t('Degree progress'),'Degree progress');
 useInputLanguage('남은 CMPSC 학점을 알려줘');assert.equal(locale.value,'ko');assert.equal(t('Degree progress'),'학위 진행률');
 useInputLanguage('How many credits remain?');assert.equal(locale.value,'en');
});
test('empty, numeric and punctuation-only input preserve the last language',()=>{
 for(const input of ['', '123', '?!'])assert.equal(detectInputLanguage(input,'ko'),'ko');
 assert.equal(detectInputLanguage(''),'en');
});
test('Hangul input with English course names remains Korean, unknown evidence stays original',()=>{
 assert.equal(detectInputLanguage('MATH 140 선수과목'),'ko');
 locale.value='ko';assert.equal(t('CMPSC 461'),'CMPSC 461');locale.value='en';
});

test('detects the ten supported languages in natural questions',()=>{
 const samples={en:'Which courses do I need to graduate?',ko:'졸업하려면 어떤 과목을 들어야 하나요?',zh:'毕业还需要修哪些课程？',ja:'卒業するためにどの授業が必要ですか？',es:'¿Cuántos créditos necesito para graduarme?',fr:'Combien de crédits me reste-t-il pour obtenir mon diplôme ?',de:'Welche Kurse brauche ich noch für meinen Abschluss?',pt:'Quantos créditos faltam para concluir minha graduação?',ru:'Какие курсы мне нужны для окончания университета?',hi:'स्नातक होने के लिए मुझे कौन से पाठ्यक्रम लेने होंगे?'};
 for(const [code,text] of Object.entries(samples))assert.equal(detectInputLanguage(text),code,text);
});
test('short greetings and course-only text do not force English',()=>{
 for(const [text,code] of [['hola','es'],['bonjour','fr'],['hallo','de'],['olá','pt'],['hello','en']])assert.equal(detectInputLanguage(text,'ko'),code);
 for(const text of ['MATH 140','CMPSC 483W / MATH 141','120','minor','PDF'])assert.equal(detectInputLanguage(text,'ja'),'ja');
});

test('landing stays English while report and response language follow input',async()=>{
 const {pageText,displayLanguage}=await import('./i18n.js');
 useInputLanguage('남은 학점을 알려줘');
 assert.equal(pageText('Your degree overview',false),'Your degree overview');
 assert.equal(displayLanguage(false),'en');
 assert.equal(locale.value,'ko');
 assert.equal(pageText('Degree progress',true),'학위 진행률');
 assert.equal(displayLanguage(true),'ko');
 // Navigating home must not change the selected response language.
 assert.equal(pageText('Attach report',false),'Attach report');assert.equal(locale.value,'ko');
 locale.value='en';
});

test('every supported translated UI dictionary has all keys and preserves placeholders',async()=>{
 const {ko}=await import('./i18n.js');
 const {default:translations}=await import('./locales/translations.js');
 const keys=Object.keys(ko).sort();
 assert.deepEqual(Object.keys(translations).sort(),['zh','ja','es','fr','de','pt','ru','hi'].sort());
 for(const [code,dict] of Object.entries(translations)){
  assert.deepEqual(Object.keys(dict).sort(),keys,code);
  for(const [key,value] of Object.entries(dict)){
   assert.equal(typeof value,'string');assert.ok(value.trim(),`${code}: ${key}`);
   assert.deepEqual(value.match(/\{\w+\}/g)||[],key.match(/\{\w+\}/g)||[],`${code}: placeholders`);
  }
  locale.value=code;
  assert.notEqual(t('Degree progress'),'Degree progress',code);
  assert.equal(t('MATH 140'),'MATH 140');
 }
 locale.value='en';
});
