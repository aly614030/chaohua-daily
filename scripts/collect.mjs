import { readFile, writeFile } from 'node:fs/promises';

const TOPICS = [
  ['时代少年团', '10080892b1f367dec8ee89a39109d0b5fe9c0d'],
  ['马嘉祺', '100808d084599a8d651890c335344604dc6441'],
  ['丁程鑫', '10080893fb74e5ad334664e9ed75257a99cb12'],
  ['宋亚轩', '100808f2892dd79cfb5ef048fd0f0926d643b0'],
  ['刘耀文', '100808b8be41fe83ba990678f3e11a63fbe041'],
  ['张真源', '1008081631871d44bb53c07db75a5ef288af6a'],
  ['严浩翔', '100808b160efbd4e3821e749d578259ab59157'],
  ['贺峻霖', '100808da1f664a39066684045432530979265a'],
];
const COOKIE = process.env.WEIBO_COOKIE;
const UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Weibo';
if (!COOKIE) throw new Error('缺少 GitHub Actions Secret：WEIBO_COOKIE');

function numeric(value) { const match=String(value).replace(/,/g,'').match(/([\d.]+)\s*([万亿]?)/); if(!match)return null; return Number(match[1])*(match[2]==='亿'?1e8:match[2]==='万'?1e4:1); }
function near(text,labels){for(const label of labels){const a=text.match(new RegExp(`${label}[^\\d]{0,80}([\\d.,]+\\s*[万亿]?)`,'i'));const b=text.match(new RegExp(`([\\d.,]+\\s*[万亿]?)[^\\d]{0,30}${label}`,'i'));const value=numeric(a?.[1]??b?.[1]);if(value!==null)return value}return null}
async function fetchText(url){const response=await fetch(url,{headers:{'User-Agent':UA,'Cookie':COOKIE,'Referer':'https://weibo.com/'},redirect:'follow'});if(!response.ok)throw new Error(`HTTP ${response.status}`);return response.text()}
async function collect(topic,topicId){const raw=await fetchText(`https://weibo.com/p/${topicId}`);const text=raw.replace(/\\u([0-9a-f]{4})/gi,(_,code)=>String.fromCharCode(parseInt(code,16))).replace(/<[^>]+>/g,' ').replace(/&nbsp;|&quot;|&#34;/g,' ');const superLike=near(text,['超Like','超like']),posts=near(text,['发帖量','新增发帖','帖子']),interactions=near(text,['互动值','发帖互动','今日互动','互动']),checkins=near(text,['签到数','签到']),reads=near(text,['阅读量','阅读']);if([superLike,posts,interactions,checkins,reads].some(value=>value===null))throw new Error('缺少必需字段');return{topic,superLike,postsWan:posts/1e4,interactionsWan:interactions/1e4,checkins,readsWan:reads/1e4,topicId}}

const now=new Date(Date.now()+8*3600*1000),date=now.toISOString().slice(0,10),collectedAt=`${date} ${now.toISOString().slice(11,16)}`;
const rows=[],errors=[];
for(const [topic,topicId] of TOPICS){try{rows.push(await collect(topic,topicId))}catch(error){errors.push(`${topic}: ${error.message}`)}}
if(rows.length!==8){console.error(errors.join('\n'));throw new Error('本次采集不完整，已保留上一份有效数据')}
const file=new URL('../data/history.json',import.meta.url),existing=JSON.parse(await readFile(file,'utf8'));
const snapshot={date,collectedAt,source:'live',rows};
existing.snapshots=[snapshot,...existing.snapshots.filter(item=>item.date!==date)].slice(0,365);
await writeFile(file,JSON.stringify(existing,null,2)+'\n','utf8');
console.log(`采集成功：${date}，${rows.length} 个超话`);
