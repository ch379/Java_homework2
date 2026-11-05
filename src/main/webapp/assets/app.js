/* Vue renders pages; Axios calls same-origin Servlets; jQuery handles dialog focus/keyboard. */
const {createApp}=Vue;
createApp({
  data(){return {
    booting:true,busy:false,loading:false,user:null,csrf:'',page:'home',registering:false,authError:'',captchaUrl:'api/captcha',
    loginForm:{name:'',password:'',captcha:''},registration:{name:'',password:'',phone:'',major:'',role:'学生',grade:'',college:'',title:'',area:'',duty:'',permissionLevel:1,entryTime:''},
    allBooks:[],bookResults:[],bookKeyword:'',availableOnly:false,bookPage:1,bookPageSize:8,
    myUnreturned:[],loanResults:[],loanFilter:{keyword:'',state:'all',start:'',end:''},
    userKeyword:'',usersSearched:false,userResults:[],copyResults:[],recommendations:[],relatedBooks:[],
    recommendForm:{bookName:'',author:'',publisher:'',price:0},statistics:{types:[],majors:[]},modal:null,toast:null,toastTimer:null,
    today:new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'short'}),
    menu:[
      {id:'home',label:'阅读首页',icon:'⌂',description:'查看你的图书馆'},
      {id:'catalog',label:'馆藏 · 借书',icon:'▤',description:'发现好书，开启一段新的阅读'},
      {id:'return',label:'归还图书',icon:'↩',description:'查看未还书籍，及时归还'},
      {id:'myLoans',label:'我的借阅',icon:'◷',permission:'myLoans',description:'回顾全部阅读记录'},
      {id:'manage',label:'图书管理',icon:'▥',permission:'manageBooks',description:'维护图书信息与馆藏副本'},
      {id:'users',label:'用户信息查询',icon:'♧',permission:'users',description:'查询读者资料与角色信息'},
      {id:'recommend',label:'推荐图书',icon:'✧',permission:'recommend',description:'让值得分享的好书加入馆藏'},
      {id:'review',label:'推荐审核',icon:'☷',permission:'recommendations',description:'审阅推荐，为馆藏添一本好书'},
      {id:'loans',label:'借阅记录查询',icon:'≡',permission:'loans',description:'按关键字、超期与日期查阅'},
      {id:'statistics',label:'借阅数据统计',icon:'▥',permission:'statistics',description:'了解不同类型与专业的借阅情况'}
    ]
  }},
  computed:{
    navigation(){return this.menu.filter(x=>!x.permission||this.user?.permissions.includes(x.permission))},
    pageTitle(){return this.menu.find(x=>x.id===this.page)?.label||'阅读首页'},
    quickActions(){return this.navigation.filter(x=>x.id!=='home').slice(0,4)},
    availableCount(){return this.allBooks.filter(b=>b.status==='正常').reduce((s,b)=>s+Number(b.available),0)},
    overdueCount(){return this.myUnreturned.filter(x=>x.status==='超期').length},
    greeting(){const h=new Date().getHours();return h<11?'上午好':h<14?'中午好':h<18?'下午好':'晚上好'},
    bookPageCount(){return Math.max(1,Math.ceil(this.bookResults.length/this.bookPageSize))},
    pagedBooks(){return this.bookResults.slice((this.bookPage-1)*this.bookPageSize,this.bookPage*this.bookPageSize)},
    bookTypes(){return [...new Set(['计算机','文学','历史','经济','哲学','艺术','教育',...this.allBooks.map(x=>x.type)])].filter(Boolean)},
    modalTitle(){if(!this.modal)return '';return {bookForm:this.modal.form?.id?'编辑图书':'添加图书',bookDetail:'图书详情',copies:'图书副本管理',userDetail:'用户资料',review:this.modal.form?.decision==='reject'?'驳回推荐':'审核推荐并入库',confirm:this.modal.title||'确认操作'}[this.modal.kind]}
  },
  methods:{
    fmt(value){return value?String(value).replace('T',' ').slice(0,16):'—'},
    money(value){return Number(value||0).toFixed(2)},
    fieldLabel(key){return {id:'用户 ID',name:'姓名',role:'角色',phone:'手机号',major:'专业',createTime:'创建时间',updateTime:'更新时间',loginTime:'上次登录',permissionLevel:'权限等级',entryTime:'入职日期',area:'负责区域',duty:'职务',grade:'年级',college:'所属学院',title:'职称'}[key]||key},
    notify(message,type='success'){clearTimeout(this.toastTimer);this.toast={message,type};this.toastTimer=setTimeout(()=>this.toast=null,type==='error'?7000:4000)},
    async api(path,data){
      try{
        const response=data===undefined?await axios.get('api'+path):await axios.post('api'+path,data,{headers:{'X-CSRF-Token':this.csrf}});
        return response.data.data;
      }catch(error){
        const message=error.response?.data?.message||'连接失败，请确认服务器已启动';
        if(error.response?.status===401){this.user=null;this.refreshCaptcha();await this.session()}
        throw new Error(message);
      }
    },
    async perform(work){if(this.busy)return;this.busy=true;try{await work()}catch(error){this.notify(error.message,'error')}finally{this.busy=false}},
    async session(){const data=await this.api('/session');this.csrf=data.csrf;this.user=data.user},
    refreshCaptcha(){this.captchaUrl='api/captcha?t='+Date.now();this.loginForm.captcha=''},
    async login(){
      this.authError='';await this.perform(async()=>{try{this.user=await this.api('/login',this.loginForm);this.page='home';this.loginForm.password='';await this.loadDashboard()}catch(error){this.authError=error.message;this.refreshCaptcha()}})
    },
    async register(){this.authError='';await this.perform(async()=>{try{await this.api('/register',this.registration);this.loginForm.name=this.registration.name;this.registration.password='';this.registering=false;this.refreshCaptcha();this.notify('注册成功，请使用新账号登录')}catch(error){this.authError=error.message}})},
    async logout(){await this.perform(async()=>{await this.api('/logout',{});this.user=null;this.modal=null;this.allBooks=[];this.myUnreturned=[];await this.session();this.refreshCaptcha()})},
    async loadDashboard(){[this.allBooks,this.myUnreturned]=await Promise.all([this.api('/books'),this.api('/my-loans?state=unreturned')])},
    async navigate(page){
      if(!this.navigation.some(x=>x.id===page))return;
      this.modal=null;this.page=page;this.loading=true;
      try{
        if(page==='home')await this.loadDashboard();
        if(['catalog','manage'].includes(page)){this.bookPage=1;await this.searchBooks()}
        if(['return','myLoans','loans'].includes(page)){this.loanFilter={keyword:'',state:page==='return'?'unreturned':'all',start:'',end:''};await this.loadLoans()}
        if(page==='users'){this.userResults=[];this.usersSearched=false;this.userKeyword=''}
        if(['recommend','review'].includes(page))await this.loadRecommendations();
        if(page==='statistics')await this.loadStatistics();
        window.scrollTo({top:0,behavior:'smooth'});
      }catch(error){this.notify(error.message,'error')}finally{this.loading=false}
    },
    async searchBooks(){try{this.bookResults=await this.api('/books?'+new URLSearchParams({keyword:this.bookKeyword,available:this.page==='catalog'&&this.availableOnly?'true':'false'}));this.bookPage=1}catch(error){this.notify(error.message,'error')}},
    async refreshBooks(){this.allBooks=await this.api('/books');if(['catalog','manage'].includes(this.page))await this.searchBooks()},
    ask(title,message,action,actionLabel='',danger=false){this.modal={kind:'confirm',title,message,action,actionLabel,danger}},
    async confirmAction(){const action=this.modal.action;await this.perform(async()=>{await action();this.modal=null})},
    borrowBook(book){this.ask('借阅图书',`确认借阅《${book.title}》？借阅期限为 ${book.days} 天，系统将分配一个可借副本。`,async()=>{const data=await this.api('/borrow',{bookId:book.id});await this.loadDashboard();await this.refreshBooks();this.notify('借阅成功，可在“归还图书”中查看记录。副本 ID：'+data.copyId)},'借阅')},
    returnBook(loan){this.ask('归还图书',`确认归还《${loan.title}》？副本 ID：${loan.bookCopyId}`,async()=>{await this.api('/return',{copyId:loan.bookCopyId});await this.loadDashboard();if(['return','myLoans'].includes(this.page))await this.loadLoans();this.notify('归还成功，借阅历史已保留')},'归还')},
    openBook(book){this.modal={kind:'bookForm',form:book?{id:book.id,title:book.title,author:book.author,type:book.type,publisher:book.publisher,days:book.days,price:book.price}:{title:'',author:'',type:'',publisher:'',days:30,price:0,stock:1,introduction:''}}},
    async saveBook(){await this.perform(async()=>{const editing=!!this.modal.form.id;await this.api(editing?'/books/edit':'/books/add',this.modal.form);this.modal=null;this.bookKeyword='';await this.refreshBooks();this.notify(editing?'图书信息已更新':'图书已添加，副本已自动创建')})},
    discardBook(book){this.ask('废弃图书',`确认将《${book.title}》标记为已废弃？所有副本必须已归还，历史借阅记录会保留。`,async()=>{await this.api('/books/discard',{id:book.id});await this.refreshBooks();this.notify('图书已标记为已废弃')},'废弃',true)},
    async openCopies(book){try{this.copyResults=await this.api('/copies?'+new URLSearchParams({bookId:book.id}));this.modal={kind:'copies',book}}catch(error){this.notify(error.message,'error')}},
    async addCopy(){await this.perform(async()=>{const book=this.modal.book;await this.api('/copies/add',{bookId:book.id});await this.openCopies(book);await this.refreshBooks();this.notify('已新增一个副本，库存已更新')})},
    async deleteCopy(copy){await this.perform(async()=>{const book=this.modal.book;await this.api('/copies/delete',{id:copy.id});await this.openCopies(book);await this.refreshBooks();this.notify('副本已删除，历史借阅记录仍保留')})},
    async loadLoans(){try{const scope=this.page==='loans'?'/loans':'/my-loans';this.loanResults=await this.api(scope+'?'+new URLSearchParams({...this.loanFilter,state:this.page==='return'?'unreturned':this.loanFilter.state}))}catch(error){this.notify(error.message,'error')}},
    async resetLoans(){this.loanFilter={keyword:'',state:this.page==='return'?'unreturned':'all',start:'',end:''};await this.loadLoans()},
    async searchUsers(){try{this.userResults=await this.api('/users?'+new URLSearchParams({keyword:this.userKeyword}));this.usersSearched=true}catch(error){this.notify(error.message,'error')}},
    async viewUser(member){try{this.modal={kind:'userDetail',member:await this.api('/users/detail?'+new URLSearchParams({id:member.id}))}}catch(error){this.notify(error.message,'error')}},
    async loadRecommendations(){try{this.recommendations=await this.api('/recommendations')}catch(error){this.notify(error.message,'error')}},
    async submitRecommendation(){await this.perform(async()=>{const result=await this.api('/recommend',this.recommendForm);this.relatedBooks=result.existing?result.books:[];if(result.existing)this.notify('馆藏中已有相关图书，可直接前往借阅');else{this.notify('推荐已提交，等待管理员审核');this.recommendForm={bookName:'',author:'',publisher:'',price:0};await this.loadRecommendations()}})},
    openReview(rec,decision){this.modal={kind:'review',rec,form:{id:rec.id,decision,type:'',days:30,stock:1,reason:''}}},
    async submitReview(){await this.perform(async()=>{const approve=this.modal.form.decision==='approve';await this.api('/recommendations/review',this.modal.form);this.modal=null;await this.loadRecommendations();await this.loadDashboard();this.notify(approve?'审核通过，图书已加入馆藏':'已驳回推荐并保存理由')})},
    async loadStatistics(){try{this.statistics=await this.api('/statistics')}catch(error){this.notify(error.message,'error')}},
    statTotal(group){return this.statistics[group].reduce((s,x)=>s+Number(x.total),0)},
    barWidth(group,total){return Number(total)/Math.max(1,...this.statistics[group].map(x=>Number(x.total)))*100},
    closeModal(){if(!this.busy)this.modal=null}
  },
  watch:{modal(value){document.body.style.overflow=value?'hidden':'';if(value)this.$nextTick(()=>$('.modal input:not([disabled]), .modal textarea, .modal button').first().trigger('focus'))}},
  async mounted(){
    axios.defaults.timeout=15000;
    $(document).on('keydown.library',event=>{if(event.key==='Escape')this.closeModal()});
    try{await this.session();if(this.user)await this.loadDashboard()}catch(error){this.notify(error.message,'error')}finally{this.booting=false;this.refreshCaptcha()}
  }
}).mount('#app');
