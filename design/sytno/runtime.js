const createdNodeIds=[];const mutatedNodeIds=[];
await Promise.all(['Regular','Medium','SemiBold','Bold','ExtraBold'].map(style=>figma.loadFontAsync({family:'Manrope',style})));
const allVars=await figma.variables.getLocalVariablesAsync();const V={};for(const v of allVars){if(v.name.startsWith('color/'))V[v.name.slice(6)]=v;else V[v.name.replace('space/','s').replace('radius/','r')]=v;}
const ST={};for(const s of await figma.getLocalTextStylesAsync())ST[s.name.replace('Сытно/','')]=s.id;
const HEX={bg:'#F7F7F0',surface:'#FFFFFF',text:'#202C25',secondary:'#627066',border:'#E2E6DF',brand:'#245D42',brandDeep:'#183E2D',brandSoft:'#E9F1E6',accent:'#D9EAA0',warning:'#8D4F15',warningSoft:'#FFF0DB',danger:'#A53832',dangerSoft:'#FCEAE7',disabled:'#EEF0E9',onBrand:'#FFFFFF',quiet:'#415148'};
function paint(k){return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:0,g:0,b:0}},'color',V[k]);}
function reg(n){createdNodeIds.push(n.id);return n;}
function bind(n,k,v){if(V[v])n.setBoundVariable(k,V[v]);}
function fill(n,k){n.fills=k?[paint(k)]:[];}
function radius(n,v){n.cornerRadius=v;bind(n,'cornerRadius','r'+v);}
function pad(n,v){for(const k of ['paddingTop','paddingRight','paddingBottom','paddingLeft']){n[k]=v;bind(n,k,'s'+v);}}
function a(parent,name,dir='VERTICAL',w=364,p=0,g=12,bg=null,r=0,h=null){const n=reg(figma.createAutoLayout(dir));n.name=name;parent.appendChild(n);n.resize(w,h||10);n.primaryAxisSizingMode=dir==='HORIZONTAL'?'FIXED':h?'FIXED':'AUTO';n.counterAxisSizingMode=dir==='HORIZONTAL'?(h?'FIXED':'AUTO'):'FIXED';pad(n,p);n.itemSpacing=g;bind(n,'itemSpacing','s'+g);fill(n,bg);if(r)radius(n,r);n.clipsContent=false;return n;}
function t(parent,str,style='Body',col='text',w=364,name=null){const n=reg(figma.createText());parent.appendChild(n);n.name=name||str.slice(0,55);n.fontName={family:'Manrope',style:'Regular'};n.textStyleId=ST[style];n.characters=str;n.fills=[paint(col)];n.textAutoResize=w===null?'WIDTH_AND_HEIGHT':'HEIGHT';if(w!==null){n.resize(w,1);n.layoutSizingHorizontal='FIXED';n.textAutoResize='HEIGHT';}return n;}
function row(parent,name,w=364,g=12,p=0,bg=null,r=0,h=null){const n=a(parent,name,'HORIZONTAL',w,p,g,bg,r,h);n.counterAxisAlignItems='CENTER';return n;}
function spacer(parent,h=8,w=1){const n=reg(figma.createFrame());parent.appendChild(n);n.name='Space';n.resize(w,h);n.fills=[];return n;}
function line(parent,w=364){const n=reg(figma.createRectangle());parent.appendChild(n);n.name='Divider';n.resize(w,1);fill(n,'border');return n;}
function stroke(n,col='border'){n.strokes=[paint(col)];n.strokeWeight=1;}
function ico(parent,key,col='text',size=24){const src=SVGS[key]||SVGS['circle-check'];const n=reg(figma.createNodeFromSvg(src.replace(/currentColor/g,HEX[col])));parent.appendChild(n);n.name='Icon / '+key;n.resize(size,size);for(const v of n.findAllWithCriteria({types:['VECTOR']})){if(v.strokes.length)v.strokes=v.strokes.map(p=>p.type==='SOLID'?figma.variables.setBoundVariableForPaint(p,'color',V[col]):p);if(v.fills.length)v.fills=v.fills.map(p=>p.type==='SOLID'?figma.variables.setBoundVariableForPaint(p,'color',V[col]):p);}return n;}
function foodPaint(index,w,h){const col=index%3,rr=Math.floor(index/3),aspect=w/h,cell=(1264/3)/(848/2);let cw=1/3,ch=1/2;if(aspect>cell)ch=(1264/848)*cw/aspect;else cw=(848/1264)*ch*aspect;return {type:'IMAGE',imageHash:ATLAS,scaleMode:'CROP',imageTransform:[[cw,0,col/3+(1/3-cw)/2],[0,ch,rr/2+(1/2-ch)/2]]};}
function photo(parent,index,w=364,h=180){const n=reg(figma.createRectangle());parent.appendChild(n);n.name='Food photo / '+index;n.resize(w,h);radius(n,20);n.fills=[foodPaint(index,w,h)];return n;}
function comp(parent,name,w,h=null,dir='VERTICAL',p=0,g=0,bg=null,r=0){const c=reg(figma.createComponent());parent.appendChild(c);c.name=name;c.resize(w,h||10);c.layoutMode=dir;c.primaryAxisSizingMode=dir==='HORIZONTAL'?'FIXED':h?'FIXED':'AUTO';c.counterAxisSizingMode=dir==='HORIZONTAL'?(h?'FIXED':'AUTO'):'FIXED';pad(c,p);c.itemSpacing=g;bind(c,'itemSpacing','s'+g);fill(c,bg);if(r)radius(c,r);c.clipsContent=false;return c;}
function prop(c,node,key,value){const k=c.addComponentProperty(key,'TEXT',value);node.componentPropertyReferences={characters:k};return k;}
function inst(parent,c,values={},w=null){const n=reg(c.createInstance());parent.appendChild(n);n.name=c.parent?.type==='COMPONENT_SET'?c.parent.name+' / '+c.name:c.name;const props={};for(const [key,val]of Object.entries(values)){const actual=Object.keys(n.componentProperties).find(k=>k===key||k.startsWith(key+'#'));if(actual)props[actual]=val;}if(Object.keys(props).length)n.setProperties(props);if(w)n.resize(w,n.height);return n;}
function allIds(root){return [root.id,...root.findAll(()=>true).map(n=>n.id)];}
