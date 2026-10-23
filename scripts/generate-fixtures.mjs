// SPDX-License-Identifier: CC0-1.0
// Requires Node.js only when regenerating fixtures; not needed by Maven or the Java library.
import {generateKeyBetween as one, generateNKeysBetween as many} from '../reference/rocicorp/index.mjs';
import {writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const rows = ['# rocicorp commit 648d2e60705bb6c7786f5f071d4e3f46deb74067; kind,left,right,count,expected (TSV)'];
function add(a,b,n=1,kind='single'){
  const keys=kind==='single'?[one(a,b)]:many(a,b,n);
  let prev=a;
  for(const k of keys){assert(prev===null||prev<k);assert(b===null||k<b);prev=k;}
  rows.push([kind,a??'-',b??'-',n,keys.join(',')].join('\t'));
  return keys;
}
for(const [a,b] of [
 [null,null],[null,'a0'],[null,'Zz'],['a0',null],['a1',null],['a0','a1'],['a1','a2'],
 ['a0V','a1'],['Zz','a0'],['Zz','a1'],[null,'Y00'],['bzz',null],['a0','a0V'],
 ['a0','a0G'],['b12','b19'],['a0','a1V'],['Zz','a01'],[null,'a0V'],[null,'b99'],
 [null,'A'+'0'.repeat(26)+'1'],['z'.repeat(26)+'y',null],['z'.repeat(27),null]
])add(a,b);
for(const [a,b] of [[null,null],[null,'a0'],['a0',null],['a0','a1'],['Zz','a0']]){
 for(const n of [0,1,2,3,10,100,1000])add(a,b,n,'batch');
}
for(const direction of ['left','right']){
 let a='a0',b='a1';
 for(let i=0;i<200;i++){const [k]=add(a,b);if(direction==='left')b=k;else a=k;}
}
let seed=42;const rnd=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;};
const list=[];
for(let i=0;i<2000;i++){
 const p=Math.floor(rnd()*(list.length+1)),a=p?list[p-1]:null,b=p<list.length?list[p]:null;
 const keys=i%8===0?add(a,b,3,'batch'):add(a,b);
 list.splice(p,0,...keys);
}
writeFileSync(new URL('../src/test/resources/rocicorp-vectors.tsv',import.meta.url),rows.join('\n')+'\n');
console.log(`Generated ${rows.length-1} fixed upstream compatibility vectors.`);
