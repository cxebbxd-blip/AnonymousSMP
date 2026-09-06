const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const mineflayer = require('mineflayer');
const nbt = require('prismarine-nbt');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const serverDir = path.join(__dirname, 'server');
const resultDir = path.join(__dirname, 'results');
fs.mkdirSync(path.join(serverDir, 'plugins'), {recursive: true});
fs.mkdirSync(resultDir, {recursive: true});
fs.copyFileSync(path.join(root, 'build/libs/AnonymousSMP.jar'), path.join(serverDir, 'plugins/AnonymousSMP.jar'));
fs.writeFileSync(path.join(serverDir, 'eula.txt'), 'eula=true\n');
fs.writeFileSync(path.join(serverDir, 'server.properties'), `server-ip=127.0.0.1
server-port=25565
online-mode=false
enforce-secure-profile=false
view-distance=2
simulation-distance=2
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
spawn-protection=0
max-players=10
sync-chunk-writes=false
network-compression-threshold=-1
`);
let log = '';
const java = spawn('java', ['-Xms512M','-Xmx1500M','-jar','paper.jar','--nogui'], {cwd: serverDir, stdio: ['pipe','pipe','pipe']});
java.stdout.on('data', data => { log += data; process.stdout.write(data); });
java.stderr.on('data', data => { log += data; process.stderr.write(data); });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const bots = [];
const results = [];
async function until(predicate, label, timeout = 15000) {
  const start = Date.now();
  while (!predicate()) {
    if (Date.now() - start > timeout) throw Error('Timed out: ' + label);
    if (java.exitCode !== null) throw Error('Server exited before ' + label);
    await sleep(50);
  }
}
function command(text) { java.stdin.write(text + '\n'); }
function passed(name) { results.push({name, passed: true}); console.log('PASS:', name); }
async function connect(username) {
  const bot = mineflayer.createBot({host: '127.0.0.1', port: 25565, username, auth: 'offline', version: '1.21.11', hideErrors: false});
  bot.events = [];
  bot.profiles = new Map();
  bot.on('error', error => console.error(username, error));
  bot.on('kicked', reason => console.error('KICKED', username, reason));
  bot._client.on('packet', (data, meta) => {
    if (['player_info','player_remove','teams','system_chat','player_chat','open_window','window_items','set_slot','sound_effect','death_combat_event'].includes(meta.name))
      bot.events.push({name: meta.name, data, at: Date.now()});
    if (meta.name === 'player_remove') for (const id of data.players) bot.profiles.delete(id);
    if (meta.name === 'player_info' && Array.isArray(data.data)) {
      for (const entry of data.data) if (entry.player) bot.profiles.set(entry.uuid, {...entry, ...entry.player});
    }
  });
  bots.push(bot);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(Error('Login timeout: '+username)), 25000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(); });
    bot.once('error', error => { clearTimeout(timer); reject(error); });
  });
  await sleep(800);
  return bot;
}
const messages = bot => bot.events.filter(e => e.name === 'system_chat').map(e => JSON.stringify(e.data));
async function main() {
  await until(() => log.includes('Done ('), 'server startup', 180000);
  assert.ok(log.includes('Enabling AnonymousSMP v1.0.2'));
  assert.ok(!log.includes('Cannot initialize'));
  passed('Paper 1.21.11 starts and loads the plugin');
  command('gamerule minecraft:locator_bar true');
  command('gamerule minecraft:send_command_feedback false');
  command('gamerule minecraft:log_admin_commands false');
  command('op Staff');
  const alice = await connect('Alice');
  const bob = await connect('Bob');
  const staff = await connect('Staff');
  await sleep(1000);
  alice.chat('/anonymous settings');
  await until(() => messages(alice).some(x => x.includes('You do not have permission to use this command.')), 'permission denial');
  const denial = messages(alice).find(x => x.includes('You do not have permission'));
  assert.ok(denial.includes('red'));
  passed('Unprivileged command receives the exact red denial');
  command('anonymous scramble');
  await until(() => [...bob.profiles.values()].some(p => p.name === '\u00a7kXXXX'), 'masked profile');
  await sleep(1500);
  for (const bot of [alice, bob, staff]) {
    assert.ok(bot.profiles.size >= 3);
    for (const entry of bot.profiles.values()) {
      assert.equal(entry.name, '\u00a7kXXXX');
      const texture = entry.properties.find(p => p.name === 'textures');
      assert.ok(texture, 'Missing Steve texture');
      const decoded = JSON.parse(Buffer.from(texture.value, 'base64'));
      assert.ok(decoded.textures.SKIN.url);
      assert.notEqual(decoded.textures.SKIN.metadata?.model, 'slim');
      assert.equal(decoded.textures.CAPE, undefined);
    }
  }
  passed('Raw profile names and skins are masked for all clients, including OPs');
  assert.ok(bob.events.some(e => e.name === 'teams' && e.data.team === 'anonymous_smp' && e.data.nameTagVisibility === 'always' && e.data.players?.includes('\u00a7kXXXX')));
  passed('Visible nametag team contains exactly the four letter scrambled name');
  const revealMark = staff.events.length;
  staff.chat('/anonymous check');
  await until(() => [...staff.profiles.values()].some(p => p.name === 'Alice'), 'staff private reveal');
  assert.ok([...bob.profiles.values()].every(p => p.name === '\u00a7kXXXX'));
  assert.ok(staff.events.slice(revealMark).some(e => e.name === 'teams' && e.data.team === 'anonymous_smp' && e.data.mode === 'remove'));
  passed('Check reveals names and removes the anonymous nametag team only for the requesting staff client');
  alice.chat('anonymous-chat-probe');
  await until(() => messages(bob).some(x => x.includes('anonymous-chat-probe')), 'anonymous chat');
  const hidden = messages(bob).find(x => x.includes('anonymous-chat-probe'));
  const visible = messages(staff).find(x => x.includes('anonymous-chat-probe'));
  assert.ok(hidden.includes('XXXX') && !hidden.includes('Alice'));
  assert.ok(visible?.includes('Alice'));
  passed('Chat sender is masked for ordinary clients and revealed to checking staff');
  const bobMark = bob.events.length;
  const staffMark = staff.events.length;
  const guest = await connect('Guest');
  await sleep(600);
  assert.ok([...guest.profiles.values()].every(p => p.name === '\u00a7kXXXX'));
  for (const e of guest.events.filter(e => e.name === 'player_info'))
    for (const entry of e.data.data || []) if (entry.player) assert.equal(entry.player.name, '\u00a7kXXXX');
  assert.ok(!bob.events.slice(bobMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('multiplayer.player.joined')));
  assert.ok(staff.events.slice(staffMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('multiplayer.player.joined')));
  passed('Players joining during scrambling receive no real player-info names; join announcement is staff-only');
  const quitMark = bob.events.length;
  const staffQuitMark = staff.events.length;
  guest.quit();
  await sleep(1000);
  assert.ok(!bob.events.slice(quitMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('multiplayer.player.left')));
  assert.ok(staff.events.slice(staffQuitMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('multiplayer.player.left')));
  passed('Quit announcement is hidden from ordinary players but visible to checking staff');
  const advMark = bob.events.length;
  const advStaffMark = staff.events.length;
  command('advancement grant Alice only minecraft:story/mine_stone');
  await sleep(1000);
  assert.ok(!bob.events.slice(advMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('chat.type.advancement')));
  assert.ok(staff.events.slice(advStaffMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('chat.type.advancement')));
  passed('Advancement announcement is staff-only');
  const deathMark = bob.events.length;
  const deathStaffMark = staff.events.length;
  const deathSelfMark = alice.events.length;
  command('kill Alice');
  await sleep(1600);
  assert.ok(!bob.events.slice(deathMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('death.')));
  assert.ok(staff.events.slice(deathStaffMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('death.')));
  assert.ok(alice.events.slice(deathSelfMark).some(e => e.name === 'death_combat_event' && JSON.stringify(e.data).includes('You died.')));
  passed('Death announcement is staff-only and the dying player receives a generic death screen');

  staff.chat('/anonymous settings');
  await until(() => staff.currentWindow, 'settings window');
  const window = staff.currentWindow;
  assert.ok(JSON.stringify(window.title).includes('AnonymousSMP'));
  for (const slot of [13,20,21,22,23,24,30,31,32]) assert.ok(window.slots[slot]);
  assert.equal(window.slots[4].name, 'player_head');
  const nametagItem = JSON.stringify(window.slots[21]);
  assert.ok(nametagItem.includes('Scramble nametags'));
  assert.ok(nametagItem.includes('Show four scrambled letters above players.'));
  passed('Single GUI opens with nine toggles, the updated nametag description and the creator head');
  const nametagMark = bob.events.length;
  await staff.clickWindow(21, 0, 0);
  await sleep(700);
  assert.ok(bob.events.slice(nametagMark).some(e => e.name === 'teams' && e.data.team === 'anonymous_smp' && e.data.mode === 'remove'));
  await staff.clickWindow(21, 0, 0);
  await sleep(700);
  assert.ok(bob.events.slice(nametagMark).some(e => e.name === 'teams' && e.data.team === 'anonymous_smp' && e.data.nameTagVisibility === 'always'));
  passed('Nametag setting removes and restores the visible scrambled team without closing the GUI');
  await staff.clickWindow(22, 0, 0);
  await sleep(700);
  assert.ok(staff.currentWindow, 'Settings GUI closed after a toggle');
  await staff.clickWindow(22, 0, 0);
  await sleep(700);
  staff.closeWindow(staff.currentWindow);
  assert.ok(staff.events.some(e => e.name === 'sound_effect'));
  const config = fs.readFileSync(path.join(serverDir, 'plugins/AnonymousSMP/config.yml'), 'utf8');
  assert.match(config, /tab: true/);
  assert.match(config, /nametags: true/);
  passed('GUI toggles remain open, play sound and persist the setting');
  for (const button of [0, 1]) {
    staff.chat('/anonymous settings');
    await until(() => staff.currentWindow, 'credits menu');
    const mark = staff.events.length;
    const otherMark = bob.events.length;
    await staff.clickWindow(4, button, 0);
    await until(() => !staff.currentWindow, 'credits menu closes');
    await until(() => staff.events.slice(mark).filter(e => e.name === 'system_chat').length >= 2, 'Discord messages');
    const chat = staff.events.slice(mark).filter(e => e.name === 'system_chat');
    assert.equal(chat.length, 2, 'Expected exactly two chat lines');
    const title = nbt.simplify(chat[0].data.content);
    const invite = nbt.simplify(chat[1].data.content);
    assert.equal(title.text, 'Join my Discord!');
    assert.equal(title.color, 'gray');
    assert.equal(Number(title.bold), 1);
    assert.equal(invite.text, 'discord.gg/Z7fYhESTH');
    assert.equal(invite.color, 'blue');
    assert.equal(Number(invite.bold), 1);
    assert.equal(invite.click_event.action, 'open_url');
    assert.equal(invite.click_event.url, 'https://discord.gg/Z7fYhESTH');
    assert.ok(!chat.some(e => e.data.isActionBar));
    assert.ok(staff.events.slice(mark).some(e => e.name === 'sound_effect'));
    assert.ok(!bob.events.slice(otherMark).some(e => e.name === 'system_chat' && JSON.stringify(e.data).includes('Join my Discord!')));
    assert.equal(fs.readFileSync(path.join(serverDir, 'plugins/AnonymousSMP/config.yml'), 'utf8'), config);
    assert.ok(!staff.inventory.items().some(item => item.name === 'player_head'));
    passed((button === 0 ? 'Left' : 'Right') + ' click on the credits head sends two correctly styled private lines with a working URL action, closes the menu and leaves settings and inventory unchanged');
  }
  staff.chat('/anonymous check');
  await until(() => [...staff.profiles.values()].every(p => p.name === '\u00a7kXXXX'), 'staff reveal off');
  passed('Check can be turned off without changing other clients');
  const unscrambleMark = bob.events.length;
  command('anonymous unscramble');
  await until(() => [...bob.profiles.values()].some(p => p.name === 'Alice'), 'profile restoration');
  assert.ok([...bob.profiles.values()].some(p => p.name === 'Staff'));
  assert.ok(bob.events.slice(unscrambleMark).some(e => e.name === 'teams' && e.data.team === 'anonymous_smp' && e.data.mode === 'remove'));
  passed('Unscramble restores real profile names and removes the anonymous nametag team');
  const restoreMark = log.length;
  command('gamerule minecraft:locator_bar');
  await sleep(500);
  assert.match(log.slice(restoreMark), /locator_bar.*true|true.*locator_bar/);
  passed('Locator gamerule is restored after unscramble');
}
(async () => {
  let error;
  try { await main(); } catch (ex) { error = ex; console.error(ex.stack); }
  finally {
    for (const bot of bots) {
      fs.writeFileSync(path.join(resultDir, bot.username+'-packets.json'), JSON.stringify(bot.events, (_, value) => typeof value === 'bigint' ? value.toString() : value, 2));
      bot.quit();
    }
    command('stop');
    await Promise.race([new Promise(resolve => java.once('exit', resolve)), sleep(20000)]);
    if (java.exitCode === null) java.kill('SIGKILL');
    fs.writeFileSync(path.join(resultDir, 'server-console.log'), log);
    fs.writeFileSync(path.join(resultDir, 'smoke-results.json'), JSON.stringify({passed: !error, tests: results, error: error?.stack, mineflayer: require('mineflayer/package.json').version}, null, 2));
  }
  process.exit(error ? 1 : 0);
})();
