(function(){
  function tick(){
    var el=document.getElementById('missionClock');
    if(el){el.textContent=new Date().toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:true});}
  }
  tick(); setInterval(tick,1000);
  if(!window.Chart) return;
  Chart.defaults.color='#8fa3c2'; Chart.defaults.borderColor='rgba(144,166,202,.14)';
  const line=(id,data,color)=>{const c=document.getElementById(id); if(!c)return; new Chart(c,{type:'line',data:{labels:['May 12','May 17','May 22','May 27','Jun 01','Jun 06','Jun 11'],datasets:[{data,borderColor:color,backgroundColor:color+'33',fill:true,tension:.38,pointRadius:0,borderWidth:3}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}},scales:{x:{grid:{display:false}},y:{grid:{color:'rgba(144,166,202,.13)'},ticks:{callback:v=>'₹'+v+'K'}}}})};
  line('revenueChart',[5,38,29,54,38,61,78,48,69,74,107],'#7c3cff');
  line('userChart',[8,14,11,17,13,22,20,27,23,31],'#1f6feb');
  line('supportChart',[19,23,18,26,16,24,21,14,18,13],'#ff3e46');
  const donut=(id,data)=>{const c=document.getElementById(id); if(!c)return; new Chart(c,{type:'doughnut',data:{labels:['Basic','Pro','Business','Enterprise'],datasets:[{data,backgroundColor:['#7c3cff','#1f6feb','#28e07b','#f5a524'],borderWidth:0,cutout:'68%'}]},options:{responsive:true,maintainAspectRatio:false,plugins:{legend:{display:false}}}})};
  donut('planChart',[145320,56780,23650,19930]); donut('mixChart',[58,52,24,8]);
})();
