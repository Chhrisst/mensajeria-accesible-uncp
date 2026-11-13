# -*- coding: utf-8 -*-
"""
Script generador del Instrumento Oficial de Investigación:
FICHA DE OBSERVACIÓN DIRECTA DE ACCESIBILIDAD Y USABILIDAD EN FORMATO WORD (.DOCX)
Diseñado para impresión directa (Ctrl + P) en hoja A4.
"""

import os
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml, OxmlElement
from docx.oxml.ns import nsdecls, qn

def set_cell_background(cell, hex_color):
    shading_elm = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    cell._tc.get_or_add_tcPr().append(shading_elm)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'<w:tcMar {nsdecls("w")}>'
                      f'<w:top w:w="{top}" w:type="dxa"/>'
                      f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
                      f'<w:left w:w="{left}" w:type="dxa"/>'
                      f'<w:right w:w="{right}" w:type="dxa"/>'
                      f'</w:tcMar>')
    tcPr.append(tcMar)

def set_table_borders(table, color="B0BEC5", sz="4"):
    tblPr = table._tbl.tblPr
    borders = parse_xml(f'<w:tblBorders {nsdecls("w")}>'
                        f'<w:top w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
                        f'<w:bottom w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
                        f'<w:left w:val="none"/>'
                        f'<w:right w:val="none"/>'
                        f'<w:insideH w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
                        f'<w:insideV w:val="single" w:sz="{sz}" w:space="0" w:color="{color}"/>'
                        f'</w:tblBorders>')
    tblPr.append(borders)

def build_document():
    doc = Document()

    # Configuración de página A4 con márgenes optimizados para impresión
    sections = doc.sections
    for s in sections:
        s.page_width = Inches(8.27)
        s.page_height = Inches(11.69)
        s.top_margin = Inches(0.6)
        s.bottom_margin = Inches(0.6)
        s.left_margin = Inches(0.7)
        s.right_margin = Inches(0.7)

    primary_color = RGBColor(27, 54, 93)   # Navy UPN #1B365D
    text_dark = RGBColor(33, 37, 41)       # Dark Slate
    gray_sub = RGBColor(100, 116, 139)

    # 1. ENCABEZADO INSTITUCIONAL
    p_header = doc.add_paragraph()
    p_header.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_header.paragraph_format.space_before = Pt(0)
    p_header.paragraph_format.space_after = Pt(2)
    run_inst = p_header.add_run("UNIVERSIDAD PRIVADA DEL NORTE\nFACULTAD DE INGENIERÍA\nCARRERA DE INGENIERÍA DE SISTEMAS COMPUTACIONALES")
    run_inst.font.name = "Calibri"
    run_inst.font.size = Pt(10)
    run_inst.font.bold = True
    run_inst.font.color.rgb = primary_color

    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_title.paragraph_format.space_before = Pt(4)
    p_title.paragraph_format.space_after = Pt(2)
    run_t = p_title.add_run("INSTRUMENTO DE INVESTIGACIÓN EXPERIMENTAL\nGUÍA Y FICHA DE OBSERVACIÓN DIRECTA DE ACCESIBILIDAD Y USABILIDAD")
    run_t.font.name = "Calibri"
    run_t.font.size = Pt(12)
    run_t.font.bold = True
    run_t.font.color.rgb = RGBColor(15, 23, 42)

    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(8)
    run_proj = p_sub.add_run("Proyecto: Aplicación Móvil de Mensajería Asistida por IA para Personas con Discapacidad Visual\nEvaluadores: Beteta Jiménez, C. | Rivera Montero, R. | Retuerto Anaya, M.")
    run_proj.font.name = "Calibri"
    run_proj.font.size = Pt(9)
    run_proj.font.italic = True
    run_proj.font.color.rgb = gray_sub

    # 2. CUADRO DE DATOS DEL PARTICIPANTE Y SESIÓN
    tbl_meta = doc.add_table(rows=3, cols=4)
    tbl_meta.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(tbl_meta, color="94A3B8", sz="6")

    meta_data = [
        [("Código Sujeto:", True), ("S - ____", False), ("Condición Visual:", True), ("[  ] Ceguera Total    [  ] Baja Visión", False)],
        [("Fecha / Hora:", True), ("____/11/2026   ___:___", False), ("Lugar de Prueba:", True), ("Sede UNCP (Presencial)", False)],
        [("Dispositivo:", True), ("Xiaomi Redmi 13C (Android 14)", False), ("Experiencia previa:", True), ("[  ] Sí usa smartphone   [  ] Primera vez", False)]
    ]

    col_widths_meta = [Inches(1.2), Inches(2.2), Inches(1.5), Inches(2.3)]
    for r_idx, row in enumerate(tbl_meta.rows):
        for c_idx, cell in enumerate(row.cells):
            cell.width = col_widths_meta[c_idx]
            set_cell_margins(cell, top=60, bottom=60, left=100, right=100)
            label, is_bold = meta_data[r_idx][c_idx]
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            r = p.add_run(label)
            r.font.name = "Calibri"
            r.font.size = Pt(8.5)
            r.font.bold = is_bold
            if is_bold:
                set_cell_background(cell, "F1F5F9")
                r.font.color.rgb = primary_color
            else:
                r.font.color.rgb = text_dark

    # 3. ESCALA DE VALORACIÓN
    p_escala = doc.add_paragraph()
    p_escala.paragraph_format.space_before = Pt(8)
    p_escala.paragraph_format.space_after = Pt(4)
    r_esc_title = p_escala.add_run("ESCALA DE EVALUACIÓN CONDUCTUAL: ")
    r_esc_title.font.bold = True
    r_esc_title.font.size = Pt(8.5)
    r_esc_desc = p_escala.add_run(
        "L = Logrado con Autonomía (3 pts)  |  "
        "A = Logrado con Asistencia / Dificultad (2 pts)  |  "
        "NL = No Logrado / Requiere ayuda (1 pto)"
    )
    r_esc_desc.font.size = Pt(8.5)
    r_esc_desc.font.color.rgb = RGBColor(71, 85, 105)

    # 4. TABLA PRINCIPAL DE EVALUACIÓN POR MÓDULOS
    modules_data = [
        {
            "header": "MÓDULO 1: ACCESO, AUTENTICACIÓN Y SEGURIDAD SENSORIAL (L-R)",
            "task": "Instrucción: 'Por favor, regístrate diciendo tu nombre y crea tu clave secreta de toques laterales.'",
            "items": [
                "1.1 Comprende la bienvenida por voz y concede el permiso de micrófono sin titubear.",
                "1.2 Dicta su nombre completo claramente ante el estímulo del asistente de voz.",
                "1.3 Dicta su nombre de usuario único (@usuario) comprendiendo la validación sonora.",
                "1.4 Localiza táctilmente las dos zonas de pantalla completa (Lado Izq / Lado Der).",
                "1.5 Ingresa su clave de 4 toques con confirmación háptica discreta (sin fuga sonora de clave)."
            ]
        },
        {
            "header": "MÓDULO 2: GESTIÓN DE CONTACTOS Y NAVEGACIÓN ASISTIDA POR IA",
            "task": "Instrucción: 'Pídele al asistente que te diga tus contactos o que abra el chat con un compañero.'",
            "items": [
                "2.1 Escucha y comprende auditivamente la lista de contactos leída por síntesis TTS.",
                "2.2 Abre una conversación específica mediante comando de voz natural (ej: 'Abre el chat con Romell').",
                "2.3 Agrega un nuevo contacto dictando su identificador único (@usuario)."
            ]
        },
        {
            "header": "MÓDULO 3: COMPOSICIÓN Y ENVÍO DE MENSAJES (COMUNICACIÓN ACTIVA)",
            "task": "Instrucción: 'Envía un mensaje que diga: Ya salí de la reunión, nos vemos en la facultad.'",
            "items": [
                "3.1 Dicta el mensaje con lenguaje coloquial/natural sin memorizar comandos rígidos.",
                "3.2 Escucha la confirmación auditiva del texto transcrito antes de su emisión.",
                "3.3 Percibe la confirmación háptica (vibración) y sonora inmediata al enviarse exitosamente."
            ]
        },
        {
            "header": "MÓDULO 4: RECEPCIÓN, LECTURA AUDITIVA Y PRIVACIDAD (E2EE)",
            "task": "Instrucción: 'Espera la llegada de un mensaje y pídele al asistente que te lo lea en voz alta.'",
            "items": [
                "4.1 Detecta la llegada del mensaje entrante mediante tono sonoro y patrón de vibración.",
                "4.2 Solicita la lectura de mensajes no leídos con comando de voz (ej: 'Léeme mis mensajes').",
                "4.3 Comprende con claridad fonética el mensaje descifrado en el dispositivo por el lector TTS."
            ]
        },
        {
            "header": "MÓDULO 5: TOLERANCIA A ERRORES Y RECUPERACIÓN ASISTIDA",
            "task": "Instrucción: 'Observar la reacción del sistema si hay silencio o ruido en el ambiente.'",
            "items": [
                "5.1 Ante habla no detectada o ruido, el sistema orienta calmadamente sin códigos de error.",
                "5.2 Logra reintentar la acción autónomamente pulsando el área táctil de gran tamaño (>48 dp)."
            ]
        }
    ]

    total_rows = 1  # Cabecera de columnas
    for m in modules_data:
        total_rows += 2 + len(m["items"]) + 1 # Header módulo + tarea + items + fila de notas

    tbl_obs = doc.add_table(rows=total_rows, cols=6)
    tbl_obs.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(tbl_obs, color="CBD5E1", sz="4")

    col_widths = [Inches(4.2), Inches(0.55), Inches(0.55), Inches(0.55), Inches(0.7), Inches(0.65)]

    # Cabecera general de columnas
    headers = ["CRITERIO DE ACCESIBILIDAD Y TAREAS OBSERVADAS", "L (3)", "A (2)", "NL (1)", "Tiempo", "Intentos"]
    hdr_cells = tbl_obs.rows[0].cells
    for i, title in enumerate(headers):
        hdr_cells[i].width = col_widths[i]
        set_cell_background(hdr_cells[i], "1E293B") # Slate 800
        set_cell_margins(hdr_cells[i], top=80, bottom=80, left=80, right=80)
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER if i > 0 else WD_ALIGN_PARAGRAPH.LEFT
        r = p.add_run(title)
        r.font.name = "Calibri"
        r.font.size = Pt(8.5)
        r.font.bold = True
        r.font.color.rgb = RGBColor(255, 255, 255)

    current_row = 1
    for m in modules_data:
        # Fila de cabecera de módulo (merge de 6 columnas)
        row_mod = tbl_obs.rows[current_row]
        current_row += 1
        cell_m = row_mod.cells[0]
        for c in row_mod.cells[1:]:
            cell_m.merge(c)
        set_cell_background(cell_m, "E2E8F0") # Slate 200
        set_cell_margins(cell_m, top=60, bottom=60, left=100, right=100)
        p_m = cell_m.paragraphs[0]
        p_m.alignment = WD_ALIGN_PARAGRAPH.LEFT
        r_m = p_m.add_run(m["header"])
        r_m.font.name = "Calibri"
        r_m.font.size = Pt(8.5)
        r_m.font.bold = True
        r_m.font.color.rgb = primary_color

        # Fila de instrucción
        row_task = tbl_obs.rows[current_row]
        current_row += 1
        cell_t = row_task.cells[0]
        for c in row_task.cells[1:]:
            cell_t.merge(c)
        set_cell_background(cell_t, "F8FAFC")
        set_cell_margins(cell_t, top=40, bottom=40, left=120, right=80)
        p_t = cell_t.paragraphs[0]
        r_t = p_t.add_run(m["task"])
        r_t.font.name = "Calibri"
        r_t.font.size = Pt(8)
        r_t.font.italic = True
        r_t.font.color.rgb = RGBColor(71, 85, 105)

        # Filas de ítems
        for item in m["items"]:
            row_item = tbl_obs.rows[current_row]
            current_row += 1
            for c_idx, cell in enumerate(row_item.cells):
                cell.width = col_widths[c_idx]
                set_cell_margins(cell, top=50, bottom=50, left=80, right=80)
                p = cell.paragraphs[0]
                if c_idx == 0:
                    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                    r = p.add_run(item)
                    r.font.name = "Calibri"
                    r.font.size = Pt(8)
                    r.font.color.rgb = text_dark
                elif c_idx in [1, 2, 3]:
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    r = p.add_run("[   ]")
                    r.font.name = "Consolas"
                    r.font.size = Pt(8.5)
                    r.font.color.rgb = RGBColor(148, 163, 184)
                elif c_idx == 4:
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    r = p.add_run("_____ s")
                    r.font.size = Pt(8)
                    r.font.color.rgb = RGBColor(148, 163, 184)
                elif c_idx == 5:
                    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    r = p.add_run("____")
                    r.font.size = Pt(8)
                    r.font.color.rgb = RGBColor(148, 163, 184)

        # Fila de Observaciones del módulo
        row_notes = tbl_obs.rows[current_row]
        current_row += 1
        cell_n = row_notes.cells[0]
        for c in row_notes.cells[1:]:
            cell_n.merge(c)
        set_cell_margins(cell_n, top=40, bottom=40, left=100, right=80)
        p_n = cell_n.paragraphs[0]
        r_n = p_n.add_run("Observaciones / Incidentes: ____________________________________________________________________________________")
        r_n.font.name = "Calibri"
        r_n.font.size = Pt(7.5)
        r_n.font.color.rgb = RGBColor(148, 163, 184)

    # 5. RESUMEN CUANTITATIVO Y REACCIONES CUALITATIVAS
    doc.add_paragraph().paragraph_format.space_before = Pt(4)

    tbl_summary = doc.add_table(rows=2, cols=4)
    tbl_summary.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(tbl_summary, color="94A3B8", sz="6")

    summary_headers = ["Puntuación Total (Max 48 pts):", "Nivel de Autonomía (%):", "Tiempo Total Cronometrado:", "Tasa de Éxito en Tareas:"]
    summary_values = ["______ / 48 pts", "______ %", "______ segundos", "______ de 5 tareas"]

    for i in range(4):
        c_h = tbl_summary.rows[0].cells[i]
        set_cell_background(c_h, "F1F5F9")
        set_cell_margins(c_h, top=40, bottom=40, left=80, right=80)
        p_h = c_h.paragraphs[0]
        p_h.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r_h = p_h.add_run(summary_headers[i])
        r_h.font.name = "Calibri"
        r_h.font.size = Pt(8)
        r_h.font.bold = True
        r_h.font.color.rgb = primary_color

        c_v = tbl_summary.rows[1].cells[i]
        set_cell_margins(c_v, top=40, bottom=40, left=80, right=80)
        p_v = c_v.paragraphs[0]
        p_v.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r_v = p_v.add_run(summary_values[i])
        r_v.font.name = "Calibri"
        r_v.font.size = Pt(8.5)
        r_v.font.bold = True
        r_v.font.color.rgb = text_dark

    # 6. ESPACIO PARA FIRMAS DE LOS INVESTIGADORES
    p_firmas = doc.add_paragraph()
    p_firmas.paragraph_format.space_before = Pt(18)
    p_firmas.paragraph_format.space_after = Pt(2)
    p_firmas.alignment = WD_ALIGN_PARAGRAPH.CENTER

    tbl_firmas = doc.add_table(rows=1, cols=3)
    tbl_firmas.alignment = WD_TABLE_ALIGNMENT.CENTER
    firmas = [
        "_____________________________\nBeteta Jiménez, Christian O.\nInvestigador UPN",
        "_____________________________\nRivera Montero, Romell J.\nInvestigador UPN",
        "_____________________________\nRetuerto Anaya, Miguel Á.\nInvestigador UPN"
    ]
    for idx, cell in enumerate(tbl_firmas.rows[0].cells):
        set_cell_margins(cell, top=20, bottom=20, left=60, right=60)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(firmas[idx])
        r.font.name = "Calibri"
        r.font.size = Pt(8)
        r.font.color.rgb = RGBColor(71, 85, 105)

    output_path = r"C:\Users\Christ\Documents\TRABAJO DE INVESTIGACION\FICHA_DE_OBSERVACION_ACCESIBILIDAD_MESSENGER.docx"
    doc.save(output_path)
    print(f"[OK] Ficha generada exitosamente en: {output_path}")

if __name__ == "__main__":
    build_document()
