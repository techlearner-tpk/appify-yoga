import {test,expect} from '@playwright/test';

test('admin configures Zoom and member stays inside the embedded meeting',async({page})=>{
 await page.goto('/login');
 await page.getByLabel('Email').fill('admin@example.test');await page.getByLabel('Password').fill('DemoPass123!');await page.getByRole('button',{name:'Sign in →'}).click();
 await page.getByRole('link',{name:'Admin portal'}).click();
 const name=`Access Browser ${Date.now()}`;
 const catalog=page.locator('form').filter({has:page.getByRole('heading',{name:'Create a program'})});
 await catalog.getByLabel('Name',{exact:true}).fill(name);await catalog.getByLabel('Description').fill('Stage 1 provider verification');await catalog.getByRole('button',{name:'Create program'}).click();await expect(page.getByText(/Program created:/)).toBeVisible();
 const form=page.locator('form').filter({has:page.getByRole('heading',{name:'Create a live session'})});
 await form.getByLabel('Program').selectOption({label:name});await form.getByLabel('Instructor').selectOption({label:'Asha Rao (instructor@example.test)'});
 await form.getByLabel('Streaming Provider').selectOption('ZOOM');
 await expect(form.getByLabel('YouTube Live ID or URL')).toHaveCount(0);
 await form.getByLabel('Meeting URL').fill('https://us02web.zoom.us/j/12345678901?pwd=browser-private');
 await form.getByLabel('Meeting Passcode (optional)').fill('browser-private');
 await form.getByRole('button',{name:'Start test session now'}).click();const created=page.getByText(/Member class link:/);await expect(created).toBeVisible();
 const sessionId=(await created.textContent())?.match(/[0-9a-f-]{36}/)?.[0];expect(sessionId).toBeTruthy();
 await page.getByRole('button',{name:'Sign out ↗'}).click();await page.goto('/register');await page.getByLabel('Your name').fill('Zoom Member');await page.getByLabel('Email').fill(`zoom-browser-${Date.now()}@example.test`);await page.getByLabel('Password').fill('DemoPass123!');await page.getByRole('button',{name:'Create account →'}).click();
 const program=page.locator('.programCard').filter({hasText:name});await program.getByRole('button',{name:'Join program →'}).click();await expect(program.getByRole('button',{name:'Enrolled ✓'})).toBeDisabled();
 await page.goto(`/live/${sessionId}`);
 await expect(page.getByRole('button',{name:'Enter class →'})).toBeVisible();
 const source=await page.content();expect(source).not.toContain('zoom.us');expect(source).not.toContain('browser-private');
 const safe=await page.evaluate(async(id)=>fetch(`/api/backend/sessions/${id}`).then(r=>r.json()),sessionId);expect(JSON.stringify(safe)).not.toMatch(/provider|zoom|browser-private/i);
 // Only the external Zoom SDK is stubbed; login, entitlement, grant and signing are real.
 await page.context().route('https://source.zoom.us/**',async route=>{
  if(route.request().url().includes('zoom-meeting-6.5.0.min.js'))return route.fulfill({contentType:'text/javascript',body:`window.ZoomMtg={setZoomJSLib(){},preLoadWasm(){},prepareWebSDK(){},i18n:{load(){},onLoad(fn){fn()}},init(o){o.success()},join(o){const c=JSON.parse(atob(o.signature.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));if(c.role!==0||c.mn!=='12345678901'||o.passWord!=='browser-private'||o.userName!=='Zoom Member')throw Error('Invalid SDK grant');document.getElementById('zmmtg-root').innerHTML='<h1>Authorized in-app meeting</h1>';o.success()}};`});
  return route.fulfill({contentType:route.request().url().endsWith('.css')?'text/css':'text/javascript',body:''});
 });
 const before=page.context().pages().length;
 await page.clock.install();
 const grantResponse=page.waitForResponse(r=>r.url().endsWith('/sdk')&&r.request().method()==='POST');
 await page.getByRole('button',{name:'Enter class →'}).click();
 const authorized=await grantResponse;expect(authorized.status()).toBe(200);expect(authorized.headers()['cache-control']).toBe('no-store');
 const grant=await authorized.json();expect(grant).not.toHaveProperty('providerJoinUrl');expect(grant).not.toHaveProperty('clientSecret');expect(grant).not.toHaveProperty('zak');
 await expect(page.frameLocator('iframe[title="Class player"]').getByRole('heading',{name:'Authorized in-app meeting'})).toBeVisible();
 await expect(page).toHaveURL(new RegExp('/live/'+sessionId+'$'));expect(page.context().pages()).toHaveLength(before);
 await expect(page.getByRole('link',{name:'Open session →'})).toHaveCount(0);
 // The short initial app grant must not unmount an already connected meeting.
 await page.clock.fastForward(130000);
 await expect(page.frameLocator('iframe[title="Class player"]').getByRole('heading',{name:'Authorized in-app meeting'})).toBeVisible();
 await page.getByRole('button',{name:'Finish class'}).click();await expect(page.locator('iframe')).toHaveCount(0);
 await expect(page.getByRole('button',{name:'Enter class →'})).toBeVisible();
 await page.getByRole('button',{name:'Enter class →'}).click();
 await expect(page.frameLocator('iframe[title="Class player"]').getByRole('heading',{name:'Authorized in-app meeting'})).toBeVisible();

});

test('admin can save a program default and change a future session override',async({page})=>{
 await page.goto('/login');await page.getByLabel('Email').fill('admin@example.test');await page.getByLabel('Password').fill('DemoPass123!');await page.getByRole('button',{name:'Sign in →'}).click();await page.getByRole('link',{name:'Admin portal'}).click();
 const name=`Provider Editor ${Date.now()}`;
 const catalog=page.locator('form').filter({has:page.getByRole('heading',{name:'Create a program'})});await catalog.getByLabel('Name',{exact:true}).fill(name);await catalog.getByLabel('Description').fill('Future stream configuration');await catalog.getByRole('button',{name:'Create program'}).click();await expect(page.getByText(/Program created:/)).toBeVisible();
 const editor=page.locator('section').filter({has:page.getByRole('heading',{name:'Streaming configuration',exact:true})});
 await editor.getByLabel('Configuration target').selectOption({label:name});await editor.getByLabel('Streaming Provider').selectOption('ZOOM');await editor.getByLabel('Meeting URL').fill('https://zoom.us/j/12345678901?pwd=editor-private');await editor.getByLabel('Meeting Passcode (optional)').fill('editor-private');await editor.getByRole('button',{name:'Save streaming configuration'}).click();await expect(editor.getByText('Streaming configuration saved.')).toBeVisible();
 const form=page.locator('form').filter({has:page.getByRole('heading',{name:'Create a live session'})});await form.getByLabel('Program').selectOption({label:name});await form.getByLabel('Instructor').selectOption({label:'Asha Rao (instructor@example.test)'});await form.getByLabel('YouTube Live ID or URL').fill('2afajz1hd-E');
 const start=new Date(Date.now()+120000);const local=new Date(start.getTime()-start.getTimezoneOffset()*60000).toISOString().slice(0,16);await form.getByLabel('Start time (optional; blank starts now)').fill(local);await form.getByRole('button',{name:'Start test session now'}).click();const created=page.getByText(/Member class link:/);await expect(created).toBeVisible();const id=(await created.textContent())?.match(/[0-9a-f-]{36}/)?.[0];expect(id).toBeTruthy();
 await editor.getByLabel('Configure').selectOption('sessions');await editor.getByLabel('Configuration target').selectOption(id!);await expect(editor.getByLabel('YouTube Live ID or URL')).toHaveValue('2afajz1hd-E');await editor.getByLabel('Streaming Provider').selectOption('ZOOM');await editor.getByLabel('Meeting URL').fill('https://zoom.us/j/12345678901?pwd=changed-private');await editor.getByLabel('Meeting Passcode (optional)').fill('changed-private');await editor.getByRole('button',{name:'Save streaming configuration'}).click();await expect(editor.getByText('Streaming configuration saved.')).toBeVisible();
 const config=await page.evaluate(async(session)=>fetch(`/api/backend/admin/sessions/${session}/stream`).then(r=>r.json()),id);expect(config.configuration.providerType).toBe('ZOOM');
});
