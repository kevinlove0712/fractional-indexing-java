// SPDX-License-Identifier: CC0-1.0
// Usage: node scripts/review/differential.mjs JAVA CLASS_PATH LABEL random|exhaustive OUTPUT_DIRECTORY
import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import {mkdirSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {generateKeyBetween as upstreamSingle, generateNKeysBetween as upstreamBatch} from '../../reference/rocicorp/index.mjs';

// Fresh generated inputs, never read the repository's TSV corpus.
const DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
const HEADS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';
const MIN = 'A' + '0'.repeat(26);
const SEEDS = [0x9e3779b9, 0x61c88647, 0xc0ffee19];
const [java, classes, label = 'java', phase = 'random', output = 'target/differential'] = process.argv.slice(2);
if (!java || !classes || !['random', 'exhaustive'].includes(phase)) {
  throw new Error('Usage: node scripts/review/differential.mjs JAVA CLASS_PATH LABEL random|exhaustive OUTPUT_DIRECTORY');
}
mkdirSync(output, {recursive:true});
const stats = {label, seeds: phase==='exhaustive'?[]:SEEDS.map(x=>'0x'+x.toString(16)), requests:0, successfulRequests:0, outputKeys:0, expectedLengthRejections:0, expectedArgumentRejections:0, declaredMinimumFixRequests:0, maxInputLength:0, maxOutputLength:0, categories:{}, categoryModes:{}, modes:{}, heads:{}, failures:[]};
let state;
function rand(n) { state ^= state << 13; state ^= state >>> 17; state ^= state << 5; return (state >>> 0) % n; }
function payload(n) { let s=''; for (let i=0;i<n;i++) s+=DIGITS[rand(62)]; return s; }
function integer(head=HEADS[rand(52)], kind=0) { const n=head<='Z'?'Z'.charCodeAt(0)-head.charCodeAt(0)+1:head.charCodeAt(0)-'a'.charCodeAt(0)+1; return head+(kind===1?'0'.repeat(n):kind===2?'z'.repeat(n):payload(n)); }
function fraction(n=rand(70)) { return n ? payload(n-1)+DIGITS[1+rand(61)] : ''; }
function safe(key) { return key===MIN ? key+'V' : key; }
function sorted(a,b) { if(a===b)b+='V'; return a<b?[a,b]:[b,a]; }
function key() { return safe(integer()+fraction()); }
let nextId=0;
function request(category,left,right,count,mode='B',maxLength=4096,maxBatch=4096,customExpected) {
  const id=nextId++;
  if(left!=null) { stats.heads[left[0]]=(stats.heads[left[0]]||0)+1; stats.maxInputLength=Math.max(stats.maxInputLength,left.length); }
  if(right!=null) { stats.heads[right[0]]=(stats.heads[right[0]]||0)+1; stats.maxInputLength=Math.max(stats.maxInputLength,right.length); }
  stats.categories[category]=(stats.categories[category]||0)+1;
  stats.modes[mode]=(stats.modes[mode]||0)+1;
  stats.categoryModes[category+':'+mode]=(stats.categoryModes[category+':'+mode]||0)+1;
  const single = mode.toLowerCase()==='s';
  if(mode===mode.toLowerCase()) {maxLength=128;maxBatch=10000;}
  let expected, values;
  if(maxLength<2 || (!single && (maxBatch<1||count<0||count>maxBatch))) expected='E';
  else if((left?.length||0)>maxLength || (right?.length||0)>maxLength) expected='L';
  else {
    try { values=customExpected ?? (single?[upstreamSingle(left,right)]:upstreamBatch(left,right,count)); }
    catch(e) { throw new Error(`Unexpected oracle failure ${JSON.stringify({category,left,right,count,mode,maxLength,maxBatch})}: ${e.stack}`); }
    if(values.some(k=>k===MIN)) throw new Error('Reserved minimum must be isolated: '+JSON.stringify({category,left,right,count}));
    let prev=left;
    for(const k of values) {
      if((prev!==null && k<=prev)||(right!==null&&k>=right))throw new Error('Oracle ordering violation');
      prev=k;
      stats.maxOutputLength=Math.max(stats.maxOutputLength,k.length);
    }
    if(values.some(k=>k.length>maxLength))expected='L';
    else {expected='O\t'+values.join(',');stats.outputKeys+=values.length;}
  }
  stats.requests++;
  if(expected==='E')stats.expectedArgumentRejections++;
  else if(expected==='L')stats.expectedLengthRejections++;
  else stats.successfulRequests++;
  return {id, category,left,right,count,mode,maxLength,maxBatch,expected,line:[id,mode,left??'~',right??'~',count,maxLength,maxBatch].join('\t')};
}

function* cases() {
  // Deterministically force all 52 heads through integer carries and borrows.
  state=SEEDS[0];
  for(const head of HEADS) {
    for(const kind of [0,1,2]) {
      const i=integer(head,kind);
      const valid=safe(i);
      for(const count of [1,2,31,63]) {
        yield request('all-head-append',valid,null,count);
        yield request('all-head-prepend',null,valid,count);
        yield request('all-head-fraction',valid,valid+'z',count);
      }
    }
  }
  // Declared exception is isolated: JS emits the reserved integer for first prepend.
  const firstRight='A'+'0'.repeat(25)+'1';
  if(upstreamSingle(null,firstRight)!==MIN) throw new Error('Pinned minimum behavior changed');
  let current=MIN+'V';
  const descending=[current];
  for(let n=1;n<=200;n++) {
    if(n>1){current=upstreamSingle(null,current);descending.push(current);}
    const expected=descending.slice().reverse();
    stats.declaredMinimumFixRequests++;
    yield request('declared-minimum-fix',null,firstRight,n,'B',4096,4096,expected);
  }
  for(const seed of SEEDS) {
    state=seed;
    let edits=['a0','a1'];
    for(let index=0;index<60000;index++) {
      const type=index%12;
      let left,right;
      const int=integer(HEADS[index%52]);
      let n=index%997===0 ? 1000 : [0,1,2,3,7,15,31][rand(7)];
      const length=index%29===0 ? 256+rand(512) : rand(65);
      const prefix=payload(length);
      let category;
      switch(type) {
      case 0: [left,right]=sorted(key(),key());category='arbitrary-pair';break;
      case 1: [left,right]=sorted(safe(int+fraction()),safe(int+fraction()));category='same-integer';break;
      case 2: {const low=rand(61);left=int+prefix+DIGITS[low]+fraction(1+rand(8));right=int+prefix+DIGITS[low+1]+fraction(1+rand(8));category='adjacent-digit-common-prefix';break;}
      case 3: left=safe(int+prefix+'V');right=left+'0'.repeat(length)+DIGITS[1+rand(61)];category='implicit-zero-padding';break;
      case 4: {const low=rand(61);left=int+prefix+DIGITS[low]+'z'.repeat(1+length);right=int+prefix+DIGITS[low+1];category='adjacent-digit-z-tail';break;}
      case 5: left=safe(integer(HEADS[index%52],2));right=null;category='carry-out';break;
      case 6: left=null;right=safe(int+fraction());category='arbitrary-prepend';break;
      case 7: left='z'+'z'.repeat(26)+prefix+'z'.repeat(1+length);right=null;category='maximum-integer-z-tail';break;
      case 8: left=null;right=MIN+'0'.repeat(length)+'1';category='minimum-integer-fraction';break;
      case 9: left=null;right=null;category='unbounded';break;
      case 10: {if(edits.length>2000)edits=['a0','a1'];const p=rand(edits.length-1);left=edits[p];right=edits[p+1];edits.splice(p+1,0,upstreamSingle(left,right));category='evolving-dense-insertions';break;}
      case 11: left=safe(int+fraction());right=null;category='arbitrary-append';break;
      }
      const boundLength=Math.max(left?.length||0,right?.length||0,2);
      let maxLength=[2,27,127,128,129,4096,boundLength,boundLength-1,boundLength+1][rand(9)];
      let maxBatch=[1,n||1,n+1,1000,4096][rand(5)];
      let mode=rand(3)===0?'S':'B';
      if(index%13===0)mode=mode.toLowerCase();
      if(index%211===0)maxLength=[0,-1,1,-2147483648][rand(4)];
      if(index%307===0)maxBatch=[0,-1,-2147483648][rand(3)];
      if(index%401===0)n=[-1,2147483647,-2147483648][rand(3)];
      yield request(category,left,right,n,mode,maxLength,maxBatch);
    }
  }
}

function* exhaustiveCases() {
  const suffixes=[''];
  for(const d of DIGITS.slice(1))suffixes.push(d);
  for(const a of DIGITS)for(const b of DIGITS.slice(1))suffixes.push(a+b);
  suffixes.sort();
  stats.exhaustiveDomain={suffixes:suffixes.length,integer:'a0',minFractionDigits:0,maxFractionDigits:2,minSuffix:suffixes[0],maxSuffix:suffixes[suffixes.length-1],pairs:suffixes.length*(suffixes.length-1)/2};
  for(let i=0;i<suffixes.length;i++) {
    for(let j=i+1;j<suffixes.length;j++) {
      const left='a0'+suffixes[i],right='a0'+suffixes[j];
      yield request('exhaustive-two-digit-single',left,right,1,'S');
      if(j===i+1)for(const n of [2,3,7,31,1000])yield request('exhaustive-adjacent-batch',left,right,n,'B');
    }
  }
}

const child=spawn(java,['-Xmx256m','-XX:-HeapDumpOnOutOfMemoryError','-cp',classes,'DifferentialRunner'],{stdio:['pipe','pipe','pipe']});
let stderr='';child.stderr.on('data',d=>stderr+=d);
const exit = new Promise((resolve,reject)=>{child.on('exit',(code,signal)=>resolve({code,signal}));child.on('error',reject);});
const rows=createInterface({input:child.stdout,crlfDelay:Infinity});
const pending=new Map();let blockResolve;let responses=0;
rows.on('line',line=>{
  const split=line.indexOf('\t');const id=Number(line.slice(0,split));const req=pending.get(id);
  if(!req){stats.failures.push({error:'Unknown response',line});return;}
  const observed=line.slice(split+1);
  if(observed!==req.expected && stats.failures.length<20)stats.failures.push({...req,observed});
  pending.delete(id);responses++;
  if(!pending.size && blockResolve){blockResolve();blockResolve=null;}
});
const started=Date.now();
const generator=phase==='exhaustive'?exhaustiveCases():cases();
let done=false;
while(!done) {
  const block=[];
  for(let i=0;i<128;i++) {const next=generator.next();if(next.done){done=true;break;}block.push(next.value);pending.set(next.value.id,next.value);}
  if(block.length) {
    const received=new Promise(resolve=>{blockResolve=resolve;});
    const watchdog=setTimeout(()=>{console.error('No progress, pending '+pending.size);child.kill('SIGKILL');process.exit(2);},60000);
    child.stdin.write(block.map(x=>x.line).join('\n')+'\n');
    await received;
    clearTimeout(watchdog);
  }
}
child.stdin.end();
stats.process=await exit;
stats.responses=responses;stats.elapsedSeconds=(Date.now()-started)/1000;stats.stderr=stderr;
writeFileSync(resolve(output,`results-${label}.json`),JSON.stringify(stats,null,2)+'\n');
console.log(JSON.stringify(stats,null,2));
if(stats.failures.length||stats.process.code!==0||responses!==stats.requests)process.exitCode=1;
