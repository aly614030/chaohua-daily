import { readFile, writeFile } from 'node:fs/promises';

const TOPICS = ['时代少年团', '马嘉祺', '丁程鑫', '宋亚轩', '刘耀文', '张真源', '严浩翔', '贺峻霖'];
const COOKIE = process.env.WEIBO_COOKIE;
const UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Weibo';
if (!COOKIE) throw new Error('缺少 GitHub Actions Secret：WEIBO_COOKIE');

function numeric(value) { const match=String(value).replace(/,/g,'').match(/([\d.]+)\s*([万亿]?)/); if(!match)return null; return Number(match[1])*(match[2]==='亿'?1e8:match[2]==='万'?1e4:1); }
function near(text,labels){for(const label of labels){const a=text.match(new RegExp(`${label}[^\\d]{0,80}([\\d.,]+\\s*[万亿]?)`,'i'));const b=text.match(new RegExp(`([\\d.,]+\\s*[万亿]?)[^\\d]{0,30}${label}`,'i'));const value=numeric(a?.[1]??b?.[1]);if(value!==null)return value}return null}
async function fetchText(url){const response=await fetch(url,{headers:{'User-Agent':UA,'Cookie':COOKIE,'Referer':'https://weibo.com/'},redirect:'follow'});if(!response.ok)throw new Error(`HTTP ${response.status}`);return response.text()}
async function resolveId(topic){const search=await fetchText(`https://s.weibo.com/weibo?q=${encodeURIComponent(`#${topic}超话#`)}`);const ids=[...search.matchAll(/(?:weibo\.com)?\/p\/(100808[a-f0-9]+)/ig)].map(match=>match[1]);if(!ids[0])throw new Error('未找到超话ID');return ids[0]}
async function collect(topic){const topicId=await resolveId(topic);const raw=await fetchText(`https://weibo.com/p/${topicId}`);const text=raw.replace(/\\u([0-9a-f]{4})/gi,(_,code)=>String.fromCharCode(parseInt(code,16))).replace(/<[^>]+>/g,' ').replace(/&nbsp;|&quot;|&#34;/g,' ');const superLike=near(text,['超Like','超like']),posts=near(text,['发帖量','新增发帖','帖子']),interactions=near(text,['互动值','发帖互动','今日互动','互动']),checkins=near(text,['签到数','签到']),reads=near(text,['阅读量','阅读']);if([superLike,posts,interactions,checkins,reads].some(value=>value===null))throw new Error('缺少必需字段');return{topic,superLike,postsWan:posts/1e4,interactionsWan:interactions/1e4,checkins,readsWan:reads/1e4,topicId}}

const now=new Date(Date.now()+8*3600*1000),date=now.toISOString().slice(0,10),collectedAt=`${date} ${now.toISOString().slice(11,16)}`;
const rows=[],errors=[];
for(const topic of TOPICS){try{rows.push(await collect(topic))}catch(error){errors.push(`${topic}: ${error.message}`)}}
if(rows.length!==8){console.error(errors.join('\n'));throw new Error('本次采集不完整，已保留上一份有效数据')}
const file=new URL('../data/history.json',import.meta.url),existing=JSON.parse(await readFile(file,'utf8'));
const snapshot={date,collectedAt,source:'live',rows};
existing.snapshots=[snapshot,...existing.snapshots.filter(item=>item.date!==date)].slice(0,365);
await writeFile(file,JSON.stringify(existing,null,2)+'\n','utf8');
console.log(`采集成功：${date}，${rows.length} 个超话`);
