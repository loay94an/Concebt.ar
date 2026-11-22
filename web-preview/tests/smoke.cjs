const { chromium } = require('playwright');
const path = require('path');
(async () => {
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});
 const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.goto('file://'+path.resolve('web-preview/index.html'));
 await page.waitForSelector('#board');
 const drawerBefore=await page.locator('#brushLibrary').evaluate(el=>({display:getComputedStyle(el).display,transform:getComputedStyle(el).transform}));
 if(drawerBefore.display!=='block' || !drawerBefore.transform || drawerBefore.transform==='none') throw new Error('Drawer is not styled/hidden before opening: '+JSON.stringify(drawerBefore));
 await page.locator('#openBrushLibrary').click(); await page.waitForSelector('#brushLibrary.open');
 const drawerState=await page.locator('#brushLibrary').evaluate(el=>({bg:getComputedStyle(el).backgroundColor,transform:getComputedStyle(el).transform}));
 if(drawerState.bg!=='rgb(32, 35, 41)' || drawerState.transform==='none') throw new Error('Drawer dark theme or slide-in animation missing: '+JSON.stringify(drawerState));
 await page.locator('#brushLibrary [data-preset="pencil"]').first().click();
 const overlayBefore=await page.locator('#colorDialog').evaluate(el=>getComputedStyle(el).display);
 if(overlayBefore!=='none') throw new Error('Color dialog is visible before opening: '+overlayBefore);
 await page.locator('#radialPicker').click(); await page.waitForSelector('#colorDialog.open');
 const overlayAfter=await page.locator('#colorDialog').evaluate(el=>getComputedStyle(el).display);
 if(overlayAfter!=='flex') throw new Error('Color dialog did not become a modal overlay: '+overlayAfter);
 await page.locator('#customColor').evaluate(el=>{el.value='#12ab89';el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));});
 await page.locator('#applyColor').click();
 const activeColor=await page.locator('#radialPicker').evaluate(el=>getComputedStyle(el).getPropertyValue('--active-color').trim());
 if(activeColor!=='#12ab89') throw new Error('Color picker did not apply selected color: '+activeColor);

 await page.locator('#openPreferences').click(); await page.waitForSelector('#preferencesDialog.open');
 await page.locator('[data-pref="stylus"]').click(); await page.locator('[data-x="preferencesDialog"]').click();
 await page.locator('#openWorkspace').click(); await page.waitForSelector('#workspaceDrawer.open');
 await page.locator('[data-grid="dots"]').click(); await page.locator('[data-x="workspaceDrawer"]').click();
 await page.locator('#export').click(); await page.waitForSelector('#exportDialog.open');
 await page.locator('[data-format="jpg"]').click(); await page.locator('[data-x="exportDialog"]').click();
 await page.locator('#board').scrollIntoViewIfNeeded();
 const box=await page.locator('#board').boundingBox();
 await page.mouse.move(box.x+100,box.y+100); await page.mouse.down();
 await page.mouse.move(box.x+160,box.y+150,{steps:5}); await page.mouse.up();

 // Exercise real two-contact touch input through Chromium's DevTools protocol.
 const cdp=await page.context().newCDPSession(page);
 const centerX=box.x+box.width/2, centerY=box.y+box.height/2;
 const before=await page.locator('.paper').evaluate(el=>el.style.transform);
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[
  {x:centerX-35,y:centerY-20,id:1},{x:centerX+35,y:centerY+20,id:2}
 ]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[
  {x:centerX-75,y:centerY-35,id:1},{x:centerX+75,y:centerY+35,id:2}
 ]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 const after=await page.locator('.paper').evaluate(el=>el.style.transform);
 const zoom=await page.locator('#zoomLabel').textContent();
 if(before===after || !/translate\(/.test(after) || !/scale\((?!1(?:\.0*)?\))/.test(after))
  throw new Error('Two-finger pinch did not change canvas transform: '+after);
 if(!zoom || zoom.trim()==='100%') throw new Error('Zoom indicator did not update: '+zoom);
 if(errors.length) throw new Error('Browser errors: '+errors.join(' | '));
 console.log('PASS: app, brush library, preferences, workspace, grid, export, drawing, and two-finger pinch zoom/pan.');
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
