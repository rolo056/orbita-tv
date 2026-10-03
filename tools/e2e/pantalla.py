#!/usr/bin/env python3
"""Lee el volcado de pantalla de uiautomator.

  pantalla.py <volcado.xml> foco            -> que elemento tiene el foco
  pantalla.py <volcado.xml> centro <texto>  -> "x y" del centro del elemento con ese texto
  pantalla.py <volcado.xml> textos          -> los textos a la vista, en orden

Sirve para dos cosas que una foto no da: saber donde quedo el foco despues de
cada tecla del mando, y encontrar un boton para tocarlo sin depender de por
donde haya que llegar a el.
"""
import re
import sys
import xml.etree.ElementTree as ET


def caja(nodo):
    m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", nodo.get("bounds", ""))
    return tuple(int(x) for x in m.groups()) if m else None


def textos_de(nodo):
    """El texto del nodo o, si no tiene, el de lo que lleva dentro."""
    propios = [nodo.get("text") or "", nodo.get("content-desc") or ""]
    propios = [t for t in propios if t]
    if propios:
        return propios
    dentro = []
    for hijo in nodo:
        dentro.extend(textos_de(hijo))
    return dentro


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    try:
        raiz = ET.parse(sys.argv[1]).getroot()
    except Exception as e:  # el volcado falla cuando la pantalla no para de moverse
        print("sin volcado (%s)" % e)
        return 1
    orden = sys.argv[2]

    if orden == "foco":
        for n in raiz.iter("node"):
            if n.get("focused") == "true":
                print("%s  %s" % (" | ".join(textos_de(n))[:90] or "(sin texto)", n.get("bounds", "")))
                return 0
        print("(nada enfocado)")
        return 0

    if orden == "centro":
        buscado = sys.argv[3]
        for n in raiz.iter("node"):
            if buscado in (n.get("text") or "", n.get("content-desc") or ""):
                c = caja(n)
                if c:
                    print("%d %d" % ((c[0] + c[2]) // 2, (c[1] + c[3]) // 2))
                    return 0
        return 1

    if orden == "textos":
        vistos = []
        for n in raiz.iter("node"):
            t = n.get("text") or ""
            if t and t not in vistos:
                vistos.append(t)
        print(" · ".join(vistos)[:1500])
        return 0

    return 2


if __name__ == "__main__":
    sys.exit(main())
