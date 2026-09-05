import { Extension, Node, mergeAttributes } from '@tiptap/core'

const styledBlockTypes = [
  'paragraph',
  'heading',
  'blockquote',
  'horizontalRule',
  'image',
  'styledSection',
  'styledDiv',
  'styledInlineDiv',
  'figure',
  'figureCaption',
]

export const paragraphStyleTypes = [
  'paragraph',
  'heading',
  'blockquote',
  'styledDiv',
  'styledInlineDiv',
  'figureCaption',
]

const paragraphStyleProperties = {
  marginTop: 'margin-top',
  marginRight: 'margin-right',
  marginBottom: 'margin-bottom',
  marginLeft: 'margin-left',
  textIndent: 'text-indent',
  blockLineHeight: 'line-height',
  letterSpacing: 'letter-spacing',
}

const managedBlockProperties = new Set([
  'text-align',
  'margin',
  ...Object.values(paragraphStyleProperties),
])

function readPreservedStyle(element) {
  if (!element?.style) return null

  const declarations = Array.from(element.style)
    // Dedicated extensions own these declarations so toolbar state stays readable.
    .filter(property => !managedBlockProperties.has(property))
    .map(property => {
      const value = element.style.getPropertyValue(property)
      const priority = element.style.getPropertyPriority(property)
      return `${property}: ${value}${priority ? ' !important' : ''}`
    })

  return declarations.length ? declarations.join('; ') : null
}

export const PreservedInlineStyle = Extension.create({
  name: 'preservedInlineStyle',
  addGlobalAttributes() {
    return [{
      types: styledBlockTypes,
      attributes: {
        inlineStyle: {
          default: null,
          parseHTML: readPreservedStyle,
          renderHTML: attributes => attributes.inlineStyle
            ? { style: attributes.inlineStyle }
            : {},
        },
      },
    }]
  },
})

export const ParagraphStyle = Extension.create({
  name: 'paragraphStyle',
  addOptions() {
    return { types: paragraphStyleTypes }
  },
  addGlobalAttributes() {
    const attributes = Object.fromEntries(Object.entries(paragraphStyleProperties).map(([name, property]) => [
      name,
      {
        default: null,
        parseHTML: element => element.style.getPropertyValue(property) || null,
        renderHTML: values => values[name] ? { style: `${property}: ${values[name]}` } : {},
      },
    ]))
    return [{ types: this.options.types, attributes }]
  },
  addCommands() {
    return {
      setParagraphStyle: attributes => ({ commands }) => {
        const results = this.options.types.map(type => commands.updateAttributes(type, attributes))
        return results.some(Boolean)
      },
      unsetParagraphStyle: attributeNames => ({ commands }) => {
        const names = Array.isArray(attributeNames) ? attributeNames : [attributeNames]
        const results = this.options.types.map(type => commands.resetAttributes(type, names))
        return results.some(Boolean)
      },
    }
  },
})

export const StyledSection = Node.create({
  name: 'styledSection',
  group: 'block',
  content: 'block*',
  parseHTML: () => [{ tag: 'section' }],
  renderHTML: ({ HTMLAttributes }) => ['section', mergeAttributes(HTMLAttributes), 0],
})

export const StyledDiv = Node.create({
  name: 'styledDiv',
  group: 'block',
  content: 'block*',
  parseHTML: () => [{ tag: 'div' }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes), 0],
})

const blockTags = new Set([
  'ADDRESS', 'ARTICLE', 'ASIDE', 'BLOCKQUOTE', 'DIV', 'FIGURE', 'FOOTER',
  'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'HEADER', 'HR', 'OL', 'P',
  'PRE', 'SECTION', 'TABLE', 'UL',
])

export const StyledInlineDiv = Node.create({
  name: 'styledInlineDiv',
  priority: 101,
  group: 'block',
  content: 'inline*',
  parseHTML: () => [{
    tag: 'div',
    getAttrs: element => Array.from(element.children).some(child => blockTags.has(child.tagName))
      ? false
      : null,
  }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes), 0],
})

export const FigureCaption = Node.create({
  name: 'figureCaption',
  content: 'inline*',
  parseHTML: () => [{ tag: 'figcaption' }],
  renderHTML: ({ HTMLAttributes }) => ['figcaption', mergeAttributes(HTMLAttributes), 0],
})

export const Figure = Node.create({
  name: 'figure',
  group: 'block',
  content: 'image figureCaption?',
  isolating: true,
  parseHTML: () => [{ tag: 'figure' }],
  renderHTML: ({ HTMLAttributes }) => ['figure', mergeAttributes(HTMLAttributes), 0],
})
