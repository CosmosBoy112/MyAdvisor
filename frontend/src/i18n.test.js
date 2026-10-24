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
