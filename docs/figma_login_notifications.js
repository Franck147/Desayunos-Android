// Run through use_figma after loading its skills. Editable Android UI proposal.
const ids=[],variableIds=[],styleIds=[];
const existing=figma.currentPage.findOne(n=>n.name==='Desayunos / Acceso y avisos');
if(existing) return {existingBoardId:existing.id,children:existing.children.map(n=>({id:n.id,name:n.name}))};
const buttons=await figma.importComponentSetByKeyAsync('ab924dce8b851fd820bd0d56da24a9c489311ae6');
const fields=await figma.importComponentSetByKeyAsync('a54980c1c99c62912ab39e9e30fe5c267c98edb4');
for(const style of ['Regular','Medium','Bold']) await figma.loadFontAsync({family:'Roboto',style});
const collections=await figma.variables.getLocalVariableCollectionsAsync();
const collection=collections.find(c=>c.name==='Desayunos / App 1.2') || figma.variables.createVariableCollection('Desayunos / App 1.2');
const mode=collection.modes[0].modeId;
const vars={},existingVars=await figma.variables.getLocalVariablesAsync();
const rgb=h=>({r:parseInt(h.slice(0,2),16)/255,g:parseInt(h.slice(2,4),16)/255,b:parseInt(h.slice(4,6),16)/255});
function token(name,value,type,scopes) {
    const primitive=existingVars.find(v=>v.name==='Primitive/'+name&&v.variableCollectionId===collection.id) || figma.variables.createVariable('Primitive/'+name,collection,type);
    primitive.scopes=[];primitive.setValueForMode(mode,value);
    const v=existingVars.find(v=>v.name===name&&v.variableCollectionId===collection.id) || figma.variables.createVariable(name,collection,type);
    v.scopes=scopes;v.setValueForMode(mode,{type:'VARIABLE_ALIAS',id:primitive.id});v.setVariableCodeSyntax('ANDROID','DesayunoTheme.'+name);vars[name]=v;variableIds.push(primitive.id,v.id);
}
for(const [name,hex] of Object.entries({green:'00695C',cream:'FFF8F6',surface:'FFFFFF',ink:'241D1A',muted:'53433F',line:'D8C2BC',pale:'B2DFDB',accent:'D84315'})) token(name,rgb(hex),'COLOR',['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE_COLOR']);
for(const [name,value] of Object.entries({gap:12,padding:20,radius:22})) token(name,value,'FLOAT',name==='radius'?['CORNER_RADIUS']:['GAP']);
const paint=name=>figma.variables.setBoundVariableForPaint({type:'SOLID',color:rgb('000000')},'color',vars[name]);
const body=await figma.importStyleByKeyAsync('1e92164361231ef1ed79f8fd256e78e0f6fbf250');
function text(parent,value,size=14,bold=false,color='ink',name=value) {
    const n=figma.createText();ids.push(n.id);n.name=name;n.fontName={family:'Roboto',style:bold?'Bold':'Regular'};n.characters=value;
    if(size===14&&!bold) n.textStyleId=body.id;
    n.fontSize=size;n.lineHeight={unit:'PIXELS',value:Math.ceil(size*1.35)};n.fills=[paint(color)];n.textAutoResize='HEIGHT';
    n.resize(parent.width-parent.paddingLeft-parent.paddingRight,Math.ceil(size*1.35));parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.textAutoResize='HEIGHT';n.layoutSizingVertical='HUG';return n;
}
function column(parent,name,width,padding=0,color=null) {
    const n=figma.createAutoLayout('VERTICAL');ids.push(n.id);n.name=name;n.resize(width,10);n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';
    n.setBoundVariable('itemSpacing',vars.gap);n.paddingLeft=padding;n.paddingRight=padding;n.paddingTop=padding;n.paddingBottom=padding;n.fills=color?[paint(color)]:[];parent.appendChild(n);return n;
}
async function fontsFor(node) {
    const all=new Map();for(const t of node.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))all.set(JSON.stringify(s.fontName),s.fontName);
    for(const font of all.values())await figma.loadFontAsync(font);
}
function record(instance) {ids.push(instance.id,...instance.findAll(()=>true).map(n=>n.id));}
async function button(parent,label) {
    const main=buttons.children.find(n=>n.type==='COMPONENT'&&n.name==='Type=Round, Size=Medium, State=Enabled');await fontsFor(main);
    const i=main.createInstance();parent.appendChild(i);record(i);i.setProperties({'Label text#58653:0':label,'Show icon#58653:51':false});
    const width=parent.width-parent.paddingLeft-parent.paddingRight;
    i.resize(width,56);i.layoutSizingHorizontal='FILL';i.layoutSizingVertical='FIXED';i.fills=[];
    for(const frame of i.findAllWithCriteria({types:['FRAME']})) {
        frame.resize(width,56);frame.layoutSizingHorizontal='FILL';frame.layoutSizingVertical='FIXED';
        if(frame.name==='Content') {frame.fills=[paint('green')];frame.cornerRadius=16;}
    }
    const labelNode=i.findOne(n=>n.type==='TEXT'&&n.name==='Label');
    labelNode.layoutSizingHorizontal='FIXED';labelNode.layoutSizingVertical='FIXED';labelNode.textAutoResize='WIDTH_AND_HEIGHT';return i;
}
const board=figma.createAutoLayout('HORIZONTAL');ids.push(board.id);board.name='Desayunos / Acceso y avisos';board.x=100;board.y=100;board.itemSpacing=48;board.fills=[];board.counterAxisAlignItems='MIN';
const login=column(board,'01 / Login cliente · Android',390,24,'cream');login.cornerRadius=28;login.itemSpacing=20;
text(login,'DESAYUNOS',20,true,'green');text(login,'Tu mañana,\na tu gusto.',34,true);text(login,'Elige tu desayuno favorito. Nosotros lo preparamos para recoger en el local.',16,false,'muted');
const roleRow=figma.createAutoLayout('HORIZONTAL');ids.push(roleRow.id);roleRow.name='Selector de acceso';roleRow.fills=[];roleRow.itemSpacing=10;login.appendChild(roleRow);
const roleVariants=[];
for(const selected of [true,false]) {
    const c=figma.createComponent();ids.push(c.id);c.name='Selected='+selected;c.layoutMode='VERTICAL';c.resize(166,10);c.primaryAxisSizingMode='AUTO';c.paddingLeft=14;c.paddingRight=14;c.paddingTop=14;c.paddingBottom=14;c.itemSpacing=4;c.cornerRadius=18;c.fills=[paint(selected?'pale':'surface')];c.strokes=[paint(selected?'green':'line')];
    text(c,selected?'Cliente':'Personal',15,true,'green');text(c,selected?'Quiero desayunar':'Cocina y administración',12,false,'muted');roleVariants.push(c);
}
const roles=figma.combineAsVariants(roleVariants,figma.currentPage);ids.push(roles.id);roles.name='Desayunos / AccessChoice';roles.description='AccessChoice in AuthScreen.kt; client or staff selection.';roles.x=1100;roles.y=100;
roleVariants.forEach((c,i)=>{c.x=i*190;c.y=0;const instance=c.createInstance();roleRow.appendChild(instance);record(instance);});
const form=column(login,'Formulario de acceso',342,20,'surface');form.cornerRadius=28;form.strokes=[paint('line')];
text(form,'Qué gusto verte',24,true);text(form,'Ingresa para pedir y seguir tu desayuno.',14,false,'muted');
for(const label of ['Correo electrónico','Contraseña']) {
    const main=fields.children.find(n=>n.type==='COMPONENT'&&n.name==='Style=Outlined, State=Enabled, Text configurations=Label text, Leading icon=False, Trailing icon=False');
    if(!main)throw new Error('Missing Material field variant');await fontsFor(main);const i=main.createInstance();form.appendChild(i);record(i);
    i.setProperties({'Label text#52808:484':label,'Show supporting text#52051:0':false});i.resize(302,56);i.layoutSizingHorizontal='FILL';
}
await button(form,'Empezar mi mañana');text(form,'Crear una cuenta de cliente',14,true,'accent');text(login,'Tu cuenta, tus pedidos. Revisa su estado desde un solo lugar.',12,false,'muted');
const center=column(board,'02 / Centro de notificaciones · Android',390,24,'cream');center.cornerRadius=28;center.itemSpacing=18;
text(center,'Notificaciones',24,true);text(center,'Todo a su tiempo.',30,true);text(center,'2 actualizaciones por leer.',14,false,'muted');
const optin=column(center,'Permiso de avisos',342,18,'pale');optin.cornerRadius=22;
text(optin,'Que no se enfríe tu desayuno',20,true,'green');text(optin,'Activa los avisos del teléfono para enterarte cuando tu pedido esté listo.');await button(optin,'Activar notificaciones');text(optin,'Los pedidos se actualizan con la app abierta.',12,false,'muted');
text(center,'Todas     Sin leer (2)',14,true,'green');text(center,'Marcar todas como leídas',14,true,'accent');
const variants=[];
for(const unread of [true,false]) {
    const c=figma.createComponent();ids.push(c.id);c.name='Unread='+unread;c.layoutMode='VERTICAL';c.resize(342,10);c.primaryAxisSizingMode='AUTO';c.paddingLeft=18;c.paddingRight=18;c.paddingTop=18;c.paddingBottom=18;c.itemSpacing=10;c.setBoundVariable('cornerRadius',vars.radius);c.fills=[paint('surface')];c.strokes=[paint('line')];
    text(c,unread?'SIN LEER':'LEÍDO',11,true,'green');const heading=text(c,'Tu desayuno está listo',18,true);const message=text(c,'Pedido #1042 · Acércate al local para recogerlo.');
    const tp=c.addComponentProperty('Title','TEXT',heading.characters);heading.componentPropertyReferences={characters:tp};const mp=c.addComponentProperty('Message','TEXT',message.characters);message.componentPropertyReferences={characters:mp};
    text(c,'22 oct. 2026, 08:30',12,false,'muted');text(c,'Ver pedido →',14,true,'green');variants.push(c);
}
const notice=figma.combineAsVariants(variants,figma.currentPage);ids.push(notice.id);notice.name='Desayunos / NoticeCard';notice.description='NotificationCenter order update; unread/read states and order destination.';notice.x=1100;notice.y=450;variants.forEach((c,i)=>{c.x=i*370;c.y=0;});
for(const [heading,message] of [['Tu desayuno está listo','Pedido #1042 · Acércate al local para recogerlo.'],['Estamos preparando tu desayuno','Pedido #1041 · Cocina está trabajando en tu pedido.']]) {
    const i=variants[0].createInstance();center.appendChild(i);record(i);const keys=Object.keys(i.componentProperties);i.setProperties({[keys.find(k=>k.startsWith('Title#'))]:heading,[keys.find(k=>k.startsWith('Message#'))]:message});
}
const counts={};for(const n of board.findAll(()=>true))counts[n.type]=(counts[n.type]||0)+1;
const result={createdNodeIds:ids,variableIds,collectionId:collection.id,boardId:board.id,screenIds:[login.id,center.id],counts,
    imageNodes:board.findAll(n=>Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')).map(n=>n.id),fonts:[...new Set(board.findAllWithCriteria({types:['TEXT']}).map(n=>n.fontName.family))],width:board.width,height:board.height};
if(result.fonts.some(f=>f!=='Roboto'))throw new Error('Font mismatch');
return result;
