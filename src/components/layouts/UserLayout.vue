<template>
  <div id="userLayout">
    <main class="login-shell" aria-label="UMM 加木企服工作台">
      <section class="brand-panel" aria-labelledby="brand-headline">
        <div class="brand-lockup">
          <div class="brand-mark" aria-hidden="true">U<span>.</span></div>
          <div class="brand-name"><strong>UMM <span>加木企服</span></strong><small>RECRUITMENT &amp; BUSINESS SERVICES</small></div>
        </div>

        <div class="brand-story">
          <p class="brand-eyebrow">CONNECT PEOPLE. CREATE POSSIBILITIES.</p>
          <h1 id="brand-headline">连接人才与机遇<br />让招聘更有章法</h1>
          <p class="brand-description">从客户需求、人才推荐到入职交付，将团队协作与业务进展汇聚一处，让每一次招聘都有据可循。</p>
        </div>

        <div class="brand-features">
          <div class="feature-card">
            <span class="feature-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87" /><circle cx="9" cy="7" r="4" /></svg></span>
            <div><strong>人才与招聘</strong><span>推荐、面试与入职有序衔接</span></div>
          </div>
          <div class="feature-card">
            <span class="feature-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><rect x="3" y="7" width="18" height="14" rx="2" /><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12a22 22 0 0 0 18 0M10 12v3h4v-3" /></svg></span>
            <div><strong>客户与合作</strong><span>客户、商机与合同协同管理</span></div>
          </div>
          <div class="feature-card feature-card--wide">
            <span class="delivery-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m9 11 3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" /></svg></span>
            <div><strong>招聘交付，全程协同</strong><span>业务推进、流程审批与回款核算</span></div>
            <span class="workspace-badge">协同工作台</span>
          </div>
        </div>
        <div class="brand-orbit" aria-hidden="true"></div>
      </section>
      <section class="form-panel" aria-label="账户登录与注册">
        <router-view />
      </section>
    </main>
    <footer class="login-footer">UMM 加木企服 <span>·</span> 让人才连接企业，让协作创造价值</footer>
  </div>
</template>

<script>
export default {
  name: 'UserLayout'
}
</script>

<style lang="less" scoped>
#userLayout {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  padding: 48px 28px 32px;
  color: #192b3e;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background:
    radial-gradient(ellipse at 85% 5%, rgba(150, 235, 215, .34), transparent 30%),
    radial-gradient(ellipse at 10% 97%, rgba(163, 215, 242, .33), transparent 27%),
    linear-gradient(135deg, #f6fafb, #f0f8f8 55%, #f4f8fc);
  position: relative;
  isolation: isolate;
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    background-image: linear-gradient(rgba(111, 154, 166, .025) 1px, transparent 1px), linear-gradient(90deg, rgba(111, 154, 166, .025) 1px, transparent 1px);
    background-size: 48px 48px;
  }
}
.login-shell {
  display: grid;
  grid-template-columns: 54% 46%;
  width: 100%;
  max-width: 1040px;
  box-sizing: border-box;
  min-height: 656px;
  overflow: hidden;
  border: 1px solid #d9e5ea;
  border-radius: 26px;
  background: #fff;
  box-shadow: 0 28px 80px rgba(38, 79, 94, .12), 0 3px 12px rgba(38, 79, 94, .025);
}
.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 48px 48px 44px;
  background: linear-gradient(140deg, #edfcf9 0%, #eff9fb 55%, #f1f7ff 100%);
  border-right: 1px solid #e1edf0;
  > :not(.brand-orbit) { position: relative; z-index: 1; }
}
.brand-lockup { display: flex; align-items: center; gap: 13px; }
.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 12px;
  background: linear-gradient(140deg, #23c8b0 0%, #0b9f98 60%, #2185bd 100%);
  box-shadow: 0 7px 18px rgba(16, 173, 157, .19);
  color: #fff;
  font-size: 24px;
  font-weight: 750;
  letter-spacing: -2px;
  span { color: #befff1; }
}
.brand-name {
  strong { display: block; font-size: 17px; line-height: 1.4; font-weight: 750; letter-spacing: .6px; }
  strong span { margin-left: 7px; font-size: 15px; font-weight: 600; letter-spacing: 1px; }
  small { display: block; margin-top: 4px; font-size: 8px; font-weight: 600; letter-spacing: 1.7px; color: #708b9e; }
}
.brand-story { margin-top: 100px; }
.brand-eyebrow { margin: 0 0 18px; color: #008c81; font-size: 10px; line-height: 1.6; font-weight: 750; letter-spacing: 1.8px; }
.brand-story h1 { margin: 0; font-size: 40px; line-height: 1.42; font-weight: 750; letter-spacing: -.8px; color: #152a3c; }
.brand-description { max-width: 420px; margin: 17px 0 0; color: #617b90; font-size: 13px; line-height: 1.95; }
.brand-features { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: auto; padding-top: 32px; }
.feature-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 14px 12px;
  border: 1px solid #d7ecee;
  border-radius: 14px;
  background: rgba(255, 255, 255, .7);
  strong { display: block; color: #304459; font-size: 12px; font-weight: 650; line-height: 1.5; }
  div > span { display: block; margin-top: 4px; color: #728a9f; font-size: 10px; line-height: 1.5; }
  svg { display: block; width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
}
.feature-icon { display: flex; align-items: center; justify-content: center; width: 33px; height: 35px; flex: 0 0 33px; color: #009c8d; background: #ddf8f2; border-radius: 10px; }
.feature-card--wide { grid-column: ~"1 / -1"; padding: 13px 15px; }
.delivery-icon { margin-right: 1px; color: #10a76d; }
.workspace-badge { margin-left: auto; padding: 4px 7px; white-space: nowrap; border-radius: 6px; color: #008b50; background: #e0f9e9; font-size: 9px; font-weight: 650; letter-spacing: 1px; }
.brand-orbit { position: absolute; right: -112px; bottom: -105px; width: 360px; height: 360px; border: 44px solid rgba(123, 213, 208, .12); border-radius: 50%; pointer-events: none; }
.form-panel { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding: 48px 48px; background: #fff; }
.login-footer { margin-top: 25px; color: #7c909c; font-size: 11px; line-height: 1.8; text-align: center; letter-spacing: .6px; span { margin: 0 8px; color: #abbec5; } }
@media (min-width: 901px) and (max-height: 820px) {
  #userLayout { padding-top: 28px; padding-bottom: 24px; }
}
@media (max-width: 1050px) {
  .brand-panel { padding: 40px 32px; }
  .brand-story h1 { font-size: 35px; }
  .form-panel { padding: 40px 32px; }
  .feature-card { padding: 12px 9px; gap: 8px; }
  .feature-card div > span { font-size: 9px; }
}
@media (max-width: 900px) {
  #userLayout { padding: 20px; }
  .login-shell { grid-template-columns: 1fr; max-width: 480px; min-height: 0; border-radius: 22px; }
  .brand-panel { padding: 22px 28px; border-right: 0; border-bottom: 1px solid #e1edf0; }
  .brand-story { margin-top: 18px; }
  .brand-eyebrow { display: none; }
  .brand-story h1 { font-size: 26px; line-height: 1.5; }
  .brand-description, .brand-features, .brand-orbit { display: none; }
  .form-panel { padding: 26px 28px 28px; }
  .login-footer { max-width: 330px; margin-top: 16px; font-size: 10px; }
}
@media (max-width: 380px) {
  #userLayout { padding: 18px 12px; }
  .brand-panel, .form-panel { padding-left: 22px; padding-right: 22px; }
  .brand-story h1 { font-size: 23px; }
}
</style>
