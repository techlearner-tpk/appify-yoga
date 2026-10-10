import {test,expect} from '@playwright/test';

test('admin configures Zoom and member uses the authorized application redirect',async({page})=>{
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
 // Verify the real authorized 303 before the browser can contact an external meeting.
 let redirectVerified=false;
 await page.context().route('**/*',async route=>{
  const target=new URL(route.request().url());
  if(target.hostname==='zoom.us'||target.hostname.endsWith('.zoom.us')){
   await route.abort();throw new Error('Browser attempted to bypass the application redirect interception');
  }
  if(!target.pathname.startsWith('/api/provider-access/'))return route.continue();
  const response=await route.fetch({maxRedirects:0});expect(response.status()).toBe(303);
  expect(response.headers().location).toBe('https://us02web.zoom.us/j/12345678901?pwd=browser-private');redirectVerified=true;
  await route.fulfill({status:200,headers:{'content-type':'text/html','cache-control':'no-store'},body:'<h1>Authorized session opened</h1>'});
 });
 await page.getByRole('button',{name:'Enter class →'}).click();
 await expect(page.getByRole('heading',{name:'Authorized session opened'})).toBeVisible();expect(redirectVerified).toBe(true);
});

test('admin can save a program default and change a future session override',async({page})=>{
 await page.goto('/login');await page.getByLabel('Email').fill('admin@example.test');await page.getByLabel('Password').fill('DemoPass123!');await page.getByRole('button',{name:'Sign in →'}).click();await page.getByRole('link',{name:'Admin portal'}).click();
 const name=`Provider Editor ${Date.now()}`;
 const catalog=page.locator('form').filter({has:page.getByRole('heading',{name:'Create a program'})});await catalog.getByLabel('Name',{exact:true}).fill(name);await catalog.getByLabel('Description').fill('Future stream configuration');await catalog.getByRole('button',{name:'Create program'}).click();await expect(page.getByText(/Program created:/)).toBeVisible();
 const editor=page.locator('section').filter({has:page.getByRole('heading',{name:'Streaming configuration',exact:true})});
 await editor.getByLabel('Configuration target').selectOption({label:name});await editor.getByLabel('Streaming Provider').selectOption('ZOOM');await editor.getByLabel('Meeting URL').fill('https://zoom.us/j/12345678901?pwd=editor-private');await editor.getByRole('button',{name:'Save streaming configuration'}).click();await expect(editor.getByText('Streaming configuration saved.')).toBeVisible();
 const form=page.locator('form').filter({has:page.getByRole('heading',{name:'Create a live session'})});await form.getByLabel('Program').selectOption({label:name});await form.getByLabel('Instructor').selectOption({label:'Asha Rao (instructor@example.test)'});await form.getByLabel('YouTube Live ID or URL').fill('2afajz1hd-E');
 const start=new Date(Date.now()+120000);const local=new Date(start.getTime()-start.getTimezoneOffset()*60000).toISOString().slice(0,16);await form.getByLabel('Start time (optional; blank starts now)').fill(local);await form.getByRole('button',{name:'Start test session now'}).click();const created=page.getByText(/Member class link:/);await expect(created).toBeVisible();const id=(await created.textContent())?.match(/[0-9a-f-]{36}/)?.[0];expect(id).toBeTruthy();
 await editor.getByLabel('Configure').selectOption('sessions');await editor.getByLabel('Configuration target').selectOption(id!);await expect(editor.getByLabel('YouTube Live ID or URL')).toHaveValue('2afajz1hd-E');await editor.getByLabel('Streaming Provider').selectOption('ZOOM');await editor.getByLabel('Meeting URL').fill('https://zoom.us/j/12345678901?pwd=changed-private');await editor.getByRole('button',{name:'Save streaming configuration'}).click();await expect(editor.getByText('Streaming configuration saved.')).toBeVisible();
 const config=await page.evaluate(async(session)=>fetch(`/api/backend/admin/sessions/${session}/stream`).then(r=>r.json()),id);expect(config.configuration.providerType).toBe('ZOOM');
});
