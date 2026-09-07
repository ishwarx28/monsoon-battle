package com.ishwarx28.monsoonbattle.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator

/** Rasterize a font already installed on the device; no system font file is redistributed. */
object Fonts {
    fun create():BitmapFont {
        val candidates=listOf("/system/fonts/Roboto-Regular.ttf", "/system/fonts/RobotoStatic-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf", "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "C:/Windows/Fonts/arial.ttf")
        for(path in candidates)try {
            val file=Gdx.files.absolute(path)
            if(!file.exists())continue
            val generator=FreeTypeFontGenerator(file)
            try {
                val params=FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                    size=64;minFilter=Texture.TextureFilter.Linear;magFilter=Texture.TextureFilter.Linear
                }
                return generator.generateFont(params)
            }finally{generator.dispose()}
        }catch(_:Exception){}
        return BitmapFont()
    }
    fun scale(font:BitmapFont,scale:Float)=font.data.setScale(scale*15f/(font.data.lineHeight/font.data.scaleY))
}
