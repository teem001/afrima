function Header(el)
  if el.level == 1 then
    return { pandoc.RawBlock('openxml', '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'), el }
  end
end
