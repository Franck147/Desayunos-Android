from pathlib import Path
import json
import re
from collections import Counter
from xml.sax.saxutils import escape

from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, KeepTogether
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor, white
from reportlab.lib.enums import TA_LEFT
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.pagesizes import A4
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output' / 'pdf'
OUT.mkdir(parents=True, exist_ok=True)
DATA = json.loads((ROOT / 'docs' / 'historias_usuario.json').read_text(encoding='utf-8'))
PDF = OUT / 'Desayunos_30_Historias_de_Usuario.pdf'
assert len(DATA) == 30 and len({s['id'] for s in DATA}) == 30
assert all(len(s['criteria']) == 3 for s in DATA)

for name, filename in [('Body','calibri.ttf'),('Bold','calibrib.ttf'),('Light','calibril.ttf')]:
    pdfmetrics.registerFont(TTFont(name, str(Path('C:/Windows/Fonts') / filename)))
pdfmetrics.registerFontFamily('Body', normal='Body', bold='Bold', italic='Body', boldItalic='Bold')
TEAL = HexColor('#155B55')
DARK = HexColor('#18312E')
MUTED = HexColor('#526863')
LINE = HexColor('#DCE7E3')
ORANGE = HexColor('#DB7840')
PALE = HexColor('#F2F7F5')
W, H = A4
WIDTH = W - 88

styles = {
    'body': ParagraphStyle('body',fontName='Body',fontSize=10.4,leading=13.4,textColor=DARK,spaceAfter=5),
    'small': ParagraphStyle('small',fontName='Body',fontSize=9,leading=11.5,textColor=MUTED),
    'title': ParagraphStyle('title',fontName='Bold',fontSize=14,leading=16.5,textColor=TEAL,spaceAfter=7),
    'section': ParagraphStyle('section',fontName='Bold',fontSize=24,leading=28,textColor=DARK,spaceAfter=8),
    'cover': ParagraphStyle('cover',fontName='Bold',fontSize=40,leading=43,textColor=DARK,spaceAfter=14),
    'intro': ParagraphStyle('intro',fontName='Light',fontSize=15,leading=21,textColor=MUTED),
    'criterion': ParagraphStyle('criterion',fontName='Body',fontSize=10,leading=12.7,textColor=DARK,leftIndent=15,firstLineIndent=-15,spaceAfter=5),
    'index': ParagraphStyle('index',fontName='Body',fontSize=8.4,leading=10.6,textColor=DARK),
    'index_head': ParagraphStyle('index_head',fontName='Bold',fontSize=8.5,leading=11,textColor=white),
}

def p(text, style='body'):
    return Paragraph(text, styles[style])

def decorations(c, doc):
    c.saveState()
    if doc.page == 1:
        c.setFillColor(TEAL); c.rect(0,H-14,W,14,fill=1,stroke=0)
        c.setFillColor(ORANGE); c.rect(44,H-91,38,5,fill=1,stroke=0)
    else:
        c.setStrokeColor(LINE); c.line(44,H-39,W-44,H-39)
        c.setFont('Bold',9); c.setFillColor(TEAL); c.drawString(44,H-29,'DESAYUNOS / HISTORIAS DE USUARIO')
        c.setFont('Body',8); c.setFillColor(MUTED); c.drawRightString(W-44,H-29,'Versión 1.1 · 22 oct. 2026')
    c.setStrokeColor(LINE); c.line(44,39,W-44,39)
    c.setFillColor(MUTED); c.setFont('Body',8)
    c.drawString(44,26,'Android + Supabase  |  Documento de requisitos')
    c.drawRightString(W-44,26,f'{doc.page:02d} / 12')
    c.restoreState()

content = [Spacer(1,53), p('DOCUMENTO DE PRODUCTO','small'), Spacer(1,20),
    p('Gestión de<br/>desayunos','cover'),
    p('30 historias de usuario<br/>para el aplicativo Android','intro'), Spacer(1,32)]
metrics=Table([[p('<b>30</b> historias'),p('<b>3</b> perfiles'),p('<b>90</b> criterios')]],colWidths=[WIDTH/3]*3)
metrics.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,-1),PALE),('BOX',(0,0),(-1,-1),0.6,LINE),('LEFTPADDING',(0,0),(-1,-1),14),('TOPPADDING',(0,0),(-1,-1),14),('BOTTOMPADDING',(0,0),(-1,-1),10)]))
content += [metrics,Spacer(1,28),p('<b>Propósito</b>'),p('Definir el comportamiento esperado del sistema para registrar clientes, vender desayunos, coordinar cocina y controlar productos, stock, pagos y caja.'),
    Spacer(1,12),p('<b>Alcance del negocio</b>'),p('Un negocio con retiro en local, cuentas de cliente y personal autorizado. Los pagos por efectivo, Yape y Plin se verifican manualmente. La jornada operativa se interpreta en America/Lima.'),
    Spacer(1,12),p('<b>Cómo leer el documento</b>'),p('Cada historia incluye actor, prioridad, referencia de avance, dependencias y tres criterios de aceptación en formato Dado / Cuando / Entonces. Los criterios describen requisitos comprobables y sirven para construir pruebas.'),
    Spacer(1,12),p('<b>Actualización 1.1</b>'),p('Interfaz adaptable, notas por producto, cobro y entrega en una transacción, historial de completados, reportes diarios y exportación CSV. El nuevo flujo de entrega requiere aplicar la migración 004 en Supabase.','small'),
    Spacer(1,12),p('Fecha: 22 de octubre de 2026  ·  Versión: 1.1','small'),p('Avance revisado sobre el código local. Las pruebas en emulador no sustituyen la comprobación en el Honor 400 ni confirman que la migración esté aplicada en el servidor.','small'),PageBreak()]

content += [p('Mapa de las 30 historias','section'),p('12 de cliente · 6 de cocina · 12 de administración','small'),Spacer(1,10)]
head=[p('ID','index_head'),p('Historia de usuario','index_head'),p('Actor','index_head'),p('Prioridad','index_head')]
rows=[head]+[[p(s['id'],'index'),p(escape(s['title']),'index'),p(s['role'],'index'),p(s['priority'],'index')] for s in DATA]
index=Table(rows,colWidths=[48,WIDTH-48-83-57,83,57],repeatRows=1,hAlign='LEFT')
index.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),TEAL),('ROWBACKGROUNDS',(0,1),(-1,-1),[white,PALE]),('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),7),('RIGHTPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),3.5),('BOTTOMPADDING',(0,0),(-1,-1),3.5),('LINEBELOW',(0,0),(-1,0),0.6,TEAL)]))
content += [index,Spacer(1,12),p('<b>Prioridades:</b> Alta = necesaria para operar; Media = mejora de gestión o experiencia.','small'),
    p('<b>Referencia de avance:</b> Base en código = existe una base funcional; Parcial = falta completar parte del flujo; Propuesta = ampliación pendiente. No equivalen a validación en producción.','small'),
    p('Las dependencias orientan el desarrollo y sus pruebas; no constituyen un cronograma ni una estimación de esfuerzo.','small'),PageBreak()]

for start in range(0,30,3):
    group=DATA[start:start+3]
    role=group[0]['role']
    label={'Cliente':'Experiencia del cliente','Cocinero':'Operación de cocina','Administrador':'Gestión del negocio'}[role]
    content += [p(label,'section'),p(f"{group[0]['id']} a {group[-1]['id']}  |  Actor: {role}",'small'),Spacer(1,13)]
    for s in group:
        body=[p(f"{s['id']}  /  {escape(s['title'])}",'title'),
            p(f"<b>Prioridad:</b> {s['priority']}   ·   <b>Avance:</b> {s['status']}",'small'),Spacer(1,6),
            p(escape(s['story'])),p('<b>Criterios de aceptación</b>','small'),Spacer(1,4)]
        for i,criterion in enumerate(s['criteria'],1):
            body.append(p(f'<b>{i}.</b> {escape(criterion)}','criterion'))
        body += [Spacer(1,3),p('<b>Dependencias:</b> '+escape(s['depends']),'small')]
        card=Table([[body]],colWidths=[WIDTH])
        card.setStyle(TableStyle([('BOX',(0,0),(-1,-1),0.7,LINE),('LINEBEFORE',(0,0),(0,-1),3,TEAL),('BACKGROUND',(0,0),(-1,-1),white),('LEFTPADDING',(0,0),(-1,-1),13),('RIGHTPADDING',(0,0),(-1,-1),13),('TOPPADDING',(0,0),(-1,-1),10),('BOTTOMPADDING',(0,0),(-1,-1),10)]))
        content.append(KeepTogether([card]))
        if s is not group[-1]:
            content.append(Spacer(1,10))
    if start < 27:
        content.append(PageBreak())

doc=SimpleDocTemplate(str(PDF),pagesize=A4,rightMargin=44,leftMargin=44,topMargin=55,bottomMargin=52,
    title='Gestión de desayunos - 30 historias de usuario',author='Proyecto Desayunos Android',subject='Requisitos funcionales y criterios de aceptación',pageCompression=1)
doc.build(content,onFirstPage=decorations,onLaterPages=decorations)
reader=PdfReader(str(PDF))
text='\n'.join(page.extract_text() or '' for page in reader.pages)
assert len(reader.pages)==12, f'Se esperaban 12 páginas: {len(reader.pages)}'
for s in DATA:
    assert s['id'] in text and s['title'] in text, s['id']
assert len(re.findall('Criterios de aceptación',text))==30
assert '\ufffd' not in text
print(json.dumps({'pdf':str(PDF),'pages':len(reader.pages),'stories':len(DATA),'criteria':sum(len(s['criteria']) for s in DATA),'status':dict(Counter(s['status'] for s in DATA))},ensure_ascii=False))
