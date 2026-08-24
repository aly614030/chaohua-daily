import { readFile, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const config=JSON.parse(await readFile(new URL('../config/topics.json',import.meta.url),'utf8'));
const TOPICS=config.topics.filter(item=>item.enabled!==false);
const COOKIE = process.env.WEIBO_COOKIE;
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/127.0 Safari/537.36';
if (!COOKIE) throw new Error('缺少 GitHub Actions Secret：WEIBO_COOKIE');

function numeric(value) { const match=String(value).replace(/,/g,'').match(/([\d.]+)\s*([万亿]?)/); if(!match)return null; return Number(match[1])*(match[2]==='亿'?1e8:match[2]==='万'?1e4:1); }
function near(text,labels){for(const label of labels){const a=text.match(new RegExp(`${label}[^\\d]{0,80}([\\d.,]+\\s*[万亿]?)`,'i'));const b=text.match(new RegExp(`([\\d.,]+\\s*[万亿]?)[^\\d]{0,30}${label}`,'i'));const value=numeric(a?.[1]??b?.[1]);if(value!==null)return value}return null}
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({userAgent:UA,locale:'zh-CN'});
const cookies=COOKIE.split(';').map(part=>part.trim()).filter(Boolean).map(part=>{const at=part.indexOf('=');return{name:part.slice(0,at),value:part.slice(at+1),domain:'.weibo.com',path:'/'}}).filter(item=>item.name&&item.value);
await context.addCookies(cookies);
async function collect(topic,topicId){const page=await context.newPage();try{await page.goto(`https://weibo.com/p/${topicId}`,{waitUntil:'domcontentloaded',timeout:60000});await page.waitForTimeout(6000);const text=await page.locator('body').innerText();const superLike=near(text,['超Like','超like']),posts=near(text,['发帖量','新增发帖','帖子']),interactions=near(text,['互动值','发帖互动','今日互动','互动']),checkins=near(text,['签到数','签到']),reads=near(text,['阅读量','阅读']);if([superLike,posts,interactions,checkins,reads].some(value=>value===null))throw new Error('渲染后仍缺少必需字段');return{topic,superLike,postsWan:posts/1e4,interactionsWan:interactions/1e4,checkins,readsWan:reads/1e4,topicId}}finally{await page.close()}}

const now=new Date(Date.now()+8*3600*1000),date=now.toISOString().slice(0,10),collectedAt=`${date} ${now.toISOString().slice(11,16)}`;
const rows=[],errors=[];
for(const {name:topic,id:topicId} of TOPICS){try{rows.push(await collect(topic,topicId))}catch(error){errors.push(`${topic}: ${error.message}`)}}
await browser.close();
if(rows.length!==TOPICS.length){console.error(errors.join('\n'));throw new Error('本次采集不完整，已保留上一份有效数据')}
const file=new URL('../data/history.json',import.meta.url),existing=JSON.parse(await readFile(file,'utf8'));
const snapshot={date,collectedAt,source:'live',rows};
existing.snapshots=[snapshot,...existing.snapshots.filter(item=>item.date!==date)].slice(0,365);
await writeFile(file,JSON.stringify(existing,null,2)+'\n','utf8');
console.log(`采集成功：${date}，${rows.length} 个超话`);
